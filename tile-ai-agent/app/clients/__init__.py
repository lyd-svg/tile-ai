"""对 Java 业务核的客户端(架构 §6.1)。

Python **不直连 MySQL、不持有业务数据副本**(不变量 4):凡是要业务数据,
一律经这里走 Java 的 `/internal/*`。本包只有一处出口 —— `java_api.JavaApiClient`。
"""
