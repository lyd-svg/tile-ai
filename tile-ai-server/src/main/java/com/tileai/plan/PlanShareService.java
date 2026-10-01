package com.tileai.plan;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.ShareResultView;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.mapper.PlanMapper;

/**
 * 分享(T-1.13;契约 {@code openapi-c.yaml} 的 share 分组;PRD §5.5)。
 *
 * <pre>
 *   POST   /api/v1/plans/{id}/share   生成分享链接(需登录,本人方案)
 *   DELETE /api/v1/plans/{id}/share   废除分享链接(需登录,本人方案)
 *   GET    /api/v1/share/{token}      公开只读分享页(**不需登录**)
 * </pre>
 *
 * <p>一次分享就是给一个方案配一个令牌({@code t_plan.share_token},V4 建了唯一键)。
 * 拿到令牌的任何人 —— 不需要账号 —— 就能看到那一份方案的<b>只读快照</b>。
 * 废除 = 把令牌置空,旧链接当场失效。
 *
 * <h2>2026-10-01 的三条决定(用户拍板)</h2>
 *
 * <ol>
 *   <li><b>二维码由前端画</b>,后端只给 {@code shareUrl}(见 {@link ShareResultView});</li>
 *   <li><b>只做「废除即失效」</b> —— 没有有效期、没有 {@code share_expires_at} 列。
 *       架构 §13.5 那句"访问范围控制"<b>上游没有展开,也没有实现</b>,
 *       不自作主张补(登记在 {@code openapi-verification.md} §43);</li>
 *   <li>失败一律 {@code 30001},措辞与归属判定完全相同 —— 理由见
 *       {@link #readShared}。</li>
 * </ol>
 *
 * <h2>这个类里没有 SecurityConfig 的改动</h2>
 *
 * <p>两条写接口靠 {@code SecurityConfig} 的 {@code /api/v1/plans/**} 前缀受保护,
 * 公开页靠"没被登记进受保护清单"而匿名可访问。这是**巧合般的正确**,
 * 所以 {@code PlanShareContractTest} / {@code PlanShareIntegrationTest}
 * 从两侧把它钉住(写接口不带 Cookie = 401;公开页不带 Cookie = 200)。
 */
@Service
public class PlanShareService {

    private static final Logger log = LoggerFactory.getLogger(PlanShareService.class);

    /**
     * 令牌的随机源。
     *
     * <p><b>{@code static final}</b>:{@code SecureRandom} 的构造(找熵源、自播种)
     * 比取一次样贵得多。每次分享都新建一个,等于把一次可以忽略的开销变成一次明显开销。
     * {@code SecureRandom} 本身是线程安全的。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 令牌的随机字节数。32 字节 = 256 bit(架构 §13.5 要求 ≥128 bit)。 */
    private static final int TOKEN_BYTES = 32;

    /** 分享页在前端站点上的路径前缀;最终链接 = {@code baseUrl + "/share/" + token}。 */
    private static final String SHARE_PATH = "/share/";

    private final PlanMapper planMapper;
    private final PlanOwnership ownership;
    private final PlanDetailAssembler assembler;
    private final ShareProperties properties;

    public PlanShareService(PlanMapper planMapper, PlanOwnership ownership,
                            PlanDetailAssembler assembler, ShareProperties properties) {
        this.planMapper = planMapper;
        this.ownership = ownership;
        this.assembler = assembler;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // 生成 / 废除
    // ------------------------------------------------------------------

    /**
     * 生成分享链接。**已经分享过就复用旧令牌**,不重新生成。
     *
     * <p><b>为什么不重新生成。</b>用户重复点「生成分享」是个很自然的动作
     * (没看到反馈、或者想再复制一次链接)。重新生成一个新令牌,后果是
     * <b>家人手里那条已经发出去的链接当场作废</b> —— 而用户完全不知道自己做错了什么。
     * 复用则天然幂等:点几次,拿到的都是同一个链接。
     *
     * <p><b>为什么用 {@code isNull} 做条件(而不是"查一下再写")。</b>
     * 用户<b>双击</b>是两个并发请求,两个都会查到"还没有令牌",然后各写各的 ——
     * 后写的把先写的覆盖掉。而先写的那条链接<b>可能已经进了用户的剪贴板</b>。
     * 把 {@code share_token IS NULL} 拼进 UPDATE 的 WHERE,这个写就变成一次
     * 比较并交换:<b>只有一个能赢,输的那个读出赢家写下的值并原样返回</b>。
     * 两次响应拿到同一个令牌。
     *
     * <p><b>为什么这个方法不带 {@code @Transactional}。</b>这是刻意的,不是漏了。
     * 上面那个"输了就重读"依赖读到的<b>是别人刚提交的新值</b>;而 MySQL 默认的
     * REPEATABLE READ 下,一个事务的读视图在<b>第一次普通 SELECT 时就固定了</b> ——
     * 如果本方法在事务里,第一次 {@code requireOwned} 之后另开的读视图就再也看不到
     * 并发写进去的令牌,输的那次会读到 null 并报 30001。<b>用户双击,第二次收到
     * "方案不存在"</b> —— 这正是要避免的效果。这里没有跨语句的不变量要靠事务守,
     * 每条语句自身原子,所以放开自动提交反而正确。
     *
     * <p>{@code updated_at} <b>显式压住不抬</b>:分享状态不在方案卡片上显示,
     * 用户没有"改过这个方案"。抬它会无理由打乱列表顺序(`updated_at desc`),
     * 而且是 {@code PlanQuoteExpiryMarker} 已经写明的禁忌。
     * <b>不写这一句不行</b> —— {@code t_plan.updated_at} 带
     * {@code ON UPDATE CURRENT_TIMESTAMP},{@code share_token} 一变 MySQL 就会自己抬,
     * 写不写 Java 代码都一样。
     *
     * @throws BusinessException 30001 —— 方案不存在、已删除或不属于本人
     */
    public ShareResultView share(long userId, long planId) {
        Plan plan = ownership.requireOwned(userId, planId);
        if (plan.getShareToken() != null) {
            return resultOf(plan.getShareToken());
        }

        String token = newToken();
        int affected = planMapper.update(null, ownership.ownedUpdate(userId, planId)
                // 只有"当前还没有令牌"才写得进去 —— 并发下这是一次比较并交换
                .isNull(Plan::getShareToken)
                .set(Plan::getShareToken, token)
                // 见方法说明:分享不抬时间戳,而 DB 的 ON UPDATE 会自己抬,必须显式压掉
                .setSql("updated_at = updated_at"));

        if (affected == 0) {
            // 并发里另一个请求先写成了 —— 返回**它**写下的令牌,不是我们刚生成的那个
            Plan current = ownership.requireOwned(userId, planId);
            if (current.getShareToken() != null) {
                return resultOf(current.getShareToken());
            }
            // 只剩一种可能:重读之前并发里刚被废除。用本类的口径报 30001
            ownership.requireAffected(0);
        }

        log.info("方案 {} 生成了分享令牌(用户 {})", planId, userId);
        return resultOf(token);
    }

    /**
     * 废除分享链接 —— 令牌置空,旧链接<b>立刻</b>打不开。
     *
     * <p><b>必须用 {@code set} 而不是 {@code updateById}。</b>MyBatis-Plus 的
     * {@code updateById} 会<b>跳过 null 字段</b> —— 用它"置空令牌"永远写不进去,
     * 而接口照样返回成功。<b>废除看起来生效了,链接还能打开,没有任何报错。</b>
     * ({@code set} 是无条件写,这是本项目 {@code set} / {@code setSql} 的既有分工。)
     *
     * <p><b>幂等由写法保证,不靠驱动设置。</b>对一份本来就没分享的方案再废除一次,
     * UPDATE 命中那一行但没有任何列的值变化 —— 这时 MySQL 报"改了 0 行"还是"匹配 1 行",
     * 取决于 JDBC 连接的 {@code useAffectedRows} 开关(默认报匹配行数,有人改了这个参数
     * 就变成 0)。所以这里<b>不去看受影响行数</b>,而是先 {@code requireOwned}
     * 拿到 30001 的出口,再无条件写。用户点两次废除永远都是成功。
     *
     * <p>{@code updated_at} 同样压住不抬,理由见 {@link #share}。
     *
     * @throws BusinessException 30001 —— 方案不存在、已删除或不属于本人
     */
    public void revoke(long userId, long planId) {
        ownership.requireOwned(userId, planId);

        planMapper.update(null, ownership.ownedUpdate(userId, planId)
                .set(Plan::getShareToken, null)
                .setSql("updated_at = updated_at"));

        log.info("方案 {} 的分享令牌已废除(用户 {})", planId, userId);
    }

    // ------------------------------------------------------------------
    // 公开只读
    // ------------------------------------------------------------------

    /**
     * 凭令牌读一份只读方案快照。**不需要登录。**
     *
     * <p><b>所有取不到的情形一律 30001,措辞与归属判定完全相同</b>
     * ({@link PlanOwnership#NOT_FOUND_MESSAGE})。四种情形刻意不区分:
     * <ol>
     *   <li>令牌从来不存在;</li>
     *   <li>令牌格式合法但没匹配上;</li>
     *   <li>分享已被<b>废除</b>(令牌被置空了);</li>
     *   <li>方案被<b>逻辑删除</b>了。</li>
     * </ol>
     *
     * <p>区分开来就等于对外提供了一个"这个令牌曾经有效过"的判据 ——
     * <b>一个可以用来枚举令牌的探针</b>。这与 {@link PlanOwnership} 把
     * "不存在 / 别人的 / 已删除"三合一({@code 30001})是同一个理由,
     * 只是探针的目标从"方案 id"换成了"令牌"。
     *
     * <p><b>令牌为空白在这里显式拦掉,不进查询。</b>理由要说准,别用"防御性编程"
     * 糊过去:本卡实现时**实测过**这件事(2026-10-01),结论是 ——
     * 把这一行删掉,查询<b>仍然</b>取不到任何行({@code eq(列, null)} 拼出来的是
     * {@code share_token = NULL},在 SQL 里永远不成立;空白串同理匹配不到),
     * 结果一样是 30001。<b>所以这一行今天不是必需的。</b>
     *
     * <p>留着它的理由是"不去依赖那个结论":{@code eq} 对 null / 空串到底怎么拼,
     * 是 MyBatis-Plus 的行为、不是我们的契约,换个版本、换个写法
     * (比如有人改成 {@code eq(StringUtils.isNotBlank(token), …)} 的写法)
     * 就可能变。而<b>这里一旦退化成"取第一条未删除的方案",就是一个不报错的
     * 越权读取</b> —— 代价与一行判断完全不成比例。
     * (如实记下:项目里别处有相反的注释断言 {@code eq} 会跳过 null,
     * 本次实测<b>没有</b>观察到那个行为,已报给用户,不在此处顺手改。)
     *
     * <p>这里<b>不走 {@code PlanOwnership}</b>:那个类判的是"是不是我的",
     * 而公开页根本没有"我"。
     *
     * @throws BusinessException 30001 —— 令牌为空、无效、已废除,或方案已删除;
     *                            90001 —— 方案在,但户型密文解不开(见
     *                            {@link PlanDetailAssembler#readLayout})
     */
    @Transactional(readOnly = true)
    public PlanDetailView readShared(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PlanOwnership.NOT_FOUND_MESSAGE);
        }

        Plan plan = planMapper.selectOne(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getShareToken, token)
                .eq(Plan::getIsDeleted, false));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, PlanOwnership.NOT_FOUND_MESSAGE);
        }

        // 与本人详情接口**同一个组装方法** —— 两边发的是同一个 schema(PlanDetail),
        // 各写一份的话,分叉了也不会有测试红(见 PlanDetailAssembler 的类注释)
        return assembler.assemble(plan);
    }

    // ------------------------------------------------------------------
    // 令牌
    // ------------------------------------------------------------------

    /**
     * 生成一个分享令牌。
     *
     * <p><b>32 字节 {@code SecureRandom} → Base64URL(无填充)= 43 个字符,256 bit。</b>
     *
     * <p><b>刻意不用 {@code UUID.randomUUID()}。</b>UUID v4 只有 <b>122 个随机位</b>
     * (其中 6 位是固定的版本号与变体位),<b>不满足架构 §13.5 的"≥128 bit"</b>。
     * 项目里别处(UUID 当 traceId、批次号)用它没问题,因为那些不是安全凭据;
     * <b>分享令牌是</b> —— 它是不登录就能换到一份用户方案的全部凭据,
     * 是这条链路上<b>唯一</b>的机密。256 bit 让穷举在物理上不可行。
     *
     * <p>{@code Base64.getUrlEncoder()} 而不是标准编码:令牌要进 URL 路径,
     * 标准 Base64 的 {@code +} {@code /} {@code =} 在 URL 里会被转义、
     * 某些场景下还会被截断。URL-safe 字母表只有 {@code A-Za-z0-9-_},43 个字符,
     * 放得进 {@code VARCHAR(64)}。
     *
     * <p>万一撞上 {@code uk_plan_share_token}(概率约 2⁻²⁵⁶),数据库会抛
     * {@code DuplicateKeyException} → 90001。<b>不重试是刻意的</b>:能撞上说明
     * 随机源出了问题,悄悄重试会把一个真实的基础设施故障掩盖成"一切正常"。
     */
    private static String newToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /**
     * 令牌 → 返回体。
     *
     * <p>{@code baseUrl} 末尾带不带 {@code /} 都行 —— 去掉再拼,
     * 否则会拼出 {@code https://x.com//share/…} 这种看着像错的链接。
     */
    private ShareResultView resultOf(String token) {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new ShareResultView(token, base + SHARE_PATH + token);
    }
}
