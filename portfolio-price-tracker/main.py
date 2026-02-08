import asyncio
import json
from datetime import datetime

import aiohttp
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseSettings
from typing import Set, Dict, Any


# ---------------- Settings ----------------
class Settings(BaseSettings):
    ALPHAVANTAGE_API_KEY: str
    SYMBOL: str = "AAPL"
    MONGODB_URI: str = "mongodb://localhost:27017"
    DB_NAME: str = "prices_db"
    COLLECTION: str = "prices"
    POLL_INTERVAL: int = 15  # seconds

    class Config:
        env_file = ".env"


settings = Settings()
app = FastAPI()


# ---------------- Mongo ----------------
mongo_client = AsyncIOMotorClient(settings.MONGODB_URI)
db = mongo_client[settings.DB_NAME]
collection = db[settings.COLLECTION]


# ---------------- WebSocket manager ----------------
class ConnectionManager:
    def __init__(self):
        self.active_connections: Set[WebSocket] = set()

    async def connect(self, ws: WebSocket):
        await ws.accept()
        self.active_connections.add(ws)

    def disconnect(self, ws: WebSocket):
        self.active_connections.discard(ws)

    async def broadcast(self, message: Dict[str, Any]):
        if not self.active_connections:
            return
        data = json.dumps(message)
        dead = []
        for ws in list(self.active_connections):
            try:
                await ws.send_text(data)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)


manager = ConnectionManager()

# ---------------- Helper: get tickers ----------------


async def get_tickers() -> list[str]:
    """
    Get list of tickers to fetch from DB.
    Expects documents like: { "symbol": "AAPL" }
    """
    cursor = db["tickers"].find({}, {"_id": 0, "symbol": 1})
    tickers = [doc["symbol"] async for doc in cursor]
    return tickers

# ---------------- Background price fetcher ----------------


async def poller_task():
    async with aiohttp.ClientSession() as session:
        while True:
            try:
                tickers = await get_tickers()
                if not tickers:
                    print("[warn] No tickers found in DB")
                for symbol in tickers:
                    url = "https://www.alphavantage.co/query"
                    params = {
                        "function": "GLOBAL_QUOTE",
                        "symbol": symbol,
                        "apikey": settings.ALPHAVANTAGE_API_KEY,
                    }
                    async with session.get(url, params=params) as resp:
                        data = await resp.json(content_type=None)

                    gq = data.get("Global Quote", {})
                    price_str = gq.get("05. price")
                    if not price_str:
                        print(f"[warn] No price for {symbol}")
                        continue

                    price = float(price_str)
                    doc = {
                        "symbol": symbol,
                        "price": price,
                        "fetched_at": datetime.utcnow(),
                    }

                    # upsert current price
                    await collection.update_one(
                        {"symbol": symbol},
                        {"$set": doc},
                        upsert=True
                    )

                    # broadcast update
                    await manager.broadcast({"type": "price_update", **doc})
                    print(f"[info] {symbol}: {price}")

            except Exception as e:
                print(f"[error] {e}")

            await asyncio.sleep(settings.POLL_INTERVAL)


# ---------------- FastAPI lifecycle ----------------
@app.on_event("startup")
async def startup():
    await collection.create_index("fetched_at")
    asyncio.create_task(poller_task())


# ---------------- WebSocket ----------------
@app.websocket("/ws/prices")
async def websocket_endpoint(ws: WebSocket):
    await manager.connect(ws)
    try:
        while True:
            # keep connection open, ignore client messages
            await ws.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(ws)
