"""服务配置。

密钥一律经环境变量注入,**不落代码、不落库**(架构 §13.6 / §12.3)。
本文件只声明字段与默认值,任何真实密钥都不得出现在这里。
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    service_name: str = "tile-ai-agent"
    port: int = 8000

    # Java 业务核 —— 同步 REST 调用目标(架构 §6.1)
    java_base_url: str = "http://localhost:8080"

    # 服务间鉴权:请求头 X-Service-Token(openapi-common.yaml#/components/securitySchemes)
    service_token: str = ""

    # 模型网关配置(架构 §7.2「实现与配置」)。
    # 厂商选型与密钥待 P0-2(模型 bake-off)结论,当前留空。
    model_llm_provider: str = ""
    model_llm_api_key: str = ""
    model_image_provider: str = ""
    model_image_api_key: str = ""
    model_embedding_provider: str = ""

    # Qdrant —— 派生索引(架构 §9)。随时可由 MySQL 全量重建,非权威存储。
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: str = ""

    # 向量维度。架构 §9.2 写"由 Embedding 网关决定(如 1024)",
    # 真实值待 P0-2 模型选型。改它需要重建集合并全量重放(索引可重建,安全)。
    index_vector_size: int = 1024

    # RabbitMQ —— 异步任务(架构 §6.2;P0-3① 已裁决一期采用 RabbitMQ)。
    # 口令只走环境变量(§13.6)。
    rabbitmq_url: str = "amqp://tileai:tileai@localhost:5672/"


settings = Settings()
