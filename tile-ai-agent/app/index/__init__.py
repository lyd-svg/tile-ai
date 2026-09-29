"""向量索引(架构 §9)。

**Qdrant 是派生索引**:权威数据在 MySQL(由 Java 持有),变更经 `queue.index.sync`
同步到这里。索引可随时全量重建,不参与业务事务。

本包只做三件事:建集合(`collections`)、把事件落到索引(`apply`)、检索(`retrieval`)。
嵌入来自 `embedding` —— 当前是确定性占位实现,真实网关属 T-1.5。
"""
