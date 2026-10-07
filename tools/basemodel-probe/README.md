# 白模实验与真机验证脚本

这套脚本是 **「按户型尺寸画一张白模当参考图,把房间的几何锁住」** 这条路线的
完整来龙去脉 —— 从"第一次试着画"到"真机跑通"。

**结论与判据记在 `openapi-verification.md` 的 §77 与 §78**;这里是可复现的工具。

## 为什么要这样一套东西

改动前,出效果图时**房间长什么样是模型编的** —— 同一个户型点两次会得到两间不同的房。
用户的原话是"确定性太差,大模型不稳定"。

解法:**把户型尺寸按精确几何画成一张图喂给模型**。§77 的实验证明它有效
(同一输入连跑 4 次,房间形状与门窗位置全部一致);§78 把它接进了生产链路并真机验证通过。

## 脚本一览

| 脚本 | 干什么 | 会调模型吗 |
| --- | --- | --- |
| `make_whitemodel.py` | **最早的原型**:按尺寸画白模(一点透视 + 门窗洞 + 描边)。零依赖 | 否 |
| `probe.py` | 对照实验第一组:**A 只给瓷砖 / B 只给白模 / C 白模+瓷砖** —— 证明白模有用 | 是 |
| `probe2.py` | 第二组:**改进提示词**(明说"参考图 1 是白模" + 按数据自动拼房间描述)—— 证明"门变镜子"是提示词的锅 | 是 |
| `probe3.py` | 第三组:**稳定性检验** —— 同一输入连跑 4 次,看是不是同一间房 | 是 |
| `probe4.py` | 第四组:**删掉对砖的文字描述**,看瓷砖还原度有没有变好 | 是 |
| `Measure.java` | **量"瓷砖像不像"的尺子** —— 比生成图与原砖的平均色差。JDK 自带读图,零依赖 | 否 |
| `dump_basemodel.py` | 把**生产模块** `app/agent/basemodel.py` 的输出 dump 成图,肉眼核对 | 否 |
| `e2e_generate.py` | **真机端到端**:走完整生产链路发一次生成并落图 | 是 |

⚠️ **`make_whitemodel.py` 是原型,`app/agent/basemodel.py` 才是生产实现** ——
后者是把前者的画法搬过去、并改成遍历 `room["openings"]` 的结果。
**要改画法,改后者;前者留作对照。**

## 怎么跑

**产物都落在 `outputs/reports/basemodel-probe/`(已 gitignore,可随时重新生成)** ——
脚本本身**不写文件到本目录**,免得跑一次就把图塞进版本库。

```powershell
# 零模型调用的(先跑这些,不花钱)
cd D:\瓷砖智能体项目
tile-ai-agent\.venv\Scripts\python.exe tools\basemodel-probe\make_whitemodel.py
tile-ai-agent\.venv\Scripts\python.exe tools\basemodel-probe\dump_basemodel.py

# 会调模型的(花钱,每个脚本约 ¥1 上下)
tile-ai-agent\.venv\Scripts\python.exe tools\basemodel-probe\probe.py

# 量色差(JDK 17 单文件即可,不用编译)
D:\dev-tools\jdk17\bin\java.exe tools\basemodel-probe\Measure.java `
    outputs\reports\basemodel-probe\tile_ref.jpg outputs\reports\basemodel-probe\*.png
```

**`e2e_generate.py` 需要 Java(8080)与 Python(8000)都在跑**,并且会
**建一个临时 C 端账号** `probe_basemodel`(走公开注册接口,没有直接写库)。

## 几个踩过的坑(都记在这里,免得下次重踩)

1. **base64 参考图可行,但白模挂在本机 `localhost` 时外部服务取不到** ——
   所以白模是转成 **base64 data URI 内嵌进请求**发出去的(§77.6 实测)。
2. **PowerShell 往管道里写会加 BOM** —— 用管道喂 JSON 给脚本时要先剥掉,
   否则 `JSON.parse` 抛错被兜底吞掉,**表现为静默失效**。
3. **`Measure.java` 量的是平均色,测不出纹理** ——
   它能抓"偏白还是偏灰",抓不住"纹路被抹平了"。要判纹理得另加坡度/纹理指标。
4. **`e2e_generate.py` 的轮询必须带登录 Cookie** ——
   第一版忘了带,每次拿 401、状态恒为空,**表现成"轮询超时",而任务其实早就失败了**。
5. **`probe*.py` 的产物目录与脚本目录 2026-10-07 起分开了** ——
   导入兄弟脚本要用 `SCRIPT_DIR`,产物写 `HERE`。混用会让导入断掉。
