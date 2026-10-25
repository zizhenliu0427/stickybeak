"""启动：python -m stickybeak_agent

设置了 SW_AGENT_COLLECTOR_BACKEND_SERVICES 时先启动 SkyWalking Python agent（与 Java 服务的约定一致），
它会给 FastAPI 入口和 httpx 调用打点，并通过 sw8 头与 Java 网关、下游 Java 服务串成一条链路。
agent 必须在导入应用之前启动，才能给 fastapi / httpx 打上补丁。
"""

import logging
import os


def main() -> None:
    if os.getenv("SW_AGENT_COLLECTOR_BACKEND_SERVICES"):
        from skywalking import agent, config

        config.init(agent_name=os.getenv("SW_AGENT_NAME", "stickybeak-agent"))
        agent.start()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)5s [%(name)s] %(message)s")
    import uvicorn

    from .config import get_settings

    uvicorn.run("stickybeak_agent.main:app", host="0.0.0.0", port=get_settings().app_port, proxy_headers=True)


if __name__ == "__main__":
    main()
