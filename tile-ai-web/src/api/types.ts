/**
 * 契约类型(openapi-common.yaml)。
 *
 * 这里只放**跨接口共用**的结构。各模块自己的请求/响应类型放各自目录,
 * 不要往这里堆 —— 否则它会变成第二个契约文件。
 *
 * 字段名一律契约里的 camelCase(CLAUDE.md:对外 JSON 用 camelCase)。
 */

/** 统一响应体。§10.1 */
export interface ApiResponse<T = unknown> {
  /** 业务码,**0 为成功**。见 errors.ts 的 ERROR_MESSAGES */
  code: number
  message: string
  data?: T | null
  /** 链路追踪 ID。出问题时把它一起报出来,后端才定位得到 */
  traceId: string
}

/** 分页数据包装,作为 ApiResponse.data 使用。§10.1 */
export interface PageData<T> {
  list: T[]
  total: number
  /** 从 1 开始 */
  page: number
  size: number
}

/** 分页查询参数。§10.1 */
export interface PageQuery {
  page?: number
  size?: number
}

/** 后台角色。架构 §4.4 */
export type Role = 'super_admin' | 'operator' | 'editor'

/**
 * C 端用户信息。§10.1、§11.1
 *
 * **不含令牌** —— 令牌走 HttpOnly Cookie,不进响应体(前端零存储)。
 */
export interface UserInfo {
  userId: number
  username: string
  nickname?: string | null
}

/** 后台账号(对外视图,不含密码)。§8.1 t_admin_user */
export interface AdminUserView {
  id: number
  username: string
  role: Role
  status?: 'enabled' | 'disabled'
  createdAt?: string
}

/** 通用开关状态。§8.2 */
export type ProductStatus = 'on_sale' | 'off_sale'

/**
 * 以下领域结构来自 **openapi-common.yaml**(不是某一份契约专有),
 * 因此放在这里供 C 端与 B 端共用 —— 两端描述的是同一批数据。
 */

/** 产品图。§8.2 t_product_image */
export interface ProductImage {
  url: string
  /** 拍摄角度,如 front / side / detail */
  angle?: string
  sort?: number
}

/** 产品库 SKU 的对外视图。§8.2、§7.1(C 端与 B 端同构) */
export interface Sku {
  /** 内部主键。契约标为**可省略** —— 缺它时无法调详情/编辑/删除(B 端) */
  id?: number
  sku: string
  name: string
  spec?: string
  material?: string
  /** 光泽度 */
  surface?: string
  /** 花色/纹理描述 */
  colorPattern?: string
  /** 单价(元/㎡) */
  pricePerSqm: number
  applicableSpaces?: string[]
  styleTags?: string[]
  status: ProductStatus
  coverImage?: string
  images?: ProductImage[]
}

/** 搭配所含 SKU 及部位。§8.2 t_recommend_combo_item */
export interface ComboItem {
  sku: string
  /** 冗余展示名(可选) */
  name?: string
  /** 恒为 floor / wall / local 三值(契约 T-9) */
  part: Part
}

/** 推荐搭配(人工维护的成套方案)。§8.2 */
export interface Combo {
  id: number
  name: string
  space: string
  style: string
  /** 参考价下限(元) */
  refPriceMin?: number
  /** 参考价上限(元) */
  refPriceMax?: number
  coverImage?: string
  status: ProductStatus
  sort?: number
  items?: ComboItem[]
}

/** 门店信息(单店单条)。§8.5 t_store、§7.6 */
export interface Store {
  id?: number
  name: string
  address: string
  phone: string
  /** 地图/导航链接 */
  mapUrl?: string | null
}

/** 部位枚举,**恒为三值、一期不含美缝**(CLAUDE.md) */
export type Part = 'floor' | 'wall' | 'local'

/** 部位瓷砖来源:用户显式选择 / AI 自动补齐。§11.2 */
export type PartSource = 'user' | 'ai'

/** 门窗开口类型(用于面积扣减)。§8.8 */
export type OpeningType = 'door' | 'window'

/** 门窗开口。§8.3 */
export interface Opening {
  type: OpeningType
  /** 宽(米) */
  w: number
  /** 高(米) */
  h: number
}

/**
 * 某空间各部位面积(㎡)。§8.3
 *
 * ⚠️ **服务端派生字段,前端只读展示、绝不重算** ——
 * 前端再推一遍就等于有了第二份实现,两边迟早分叉。
 * 派生规则见架构 §8.3:`floor = 长×宽`、`wall = 2×(长+宽)×层高`、`local = 0`。
 */
export interface LayoutAreas {
  floor?: number
  wall?: number
  local?: number
}

/** 户型中的单个空间。§8.3 */
export interface LayoutSpace {
  /** 空间类型,取值以字典为准(契约 enum 只是内置默认值) */
  type: string
  /** 中文名(展示用) */
  name?: string
  /** 长(米) */
  length: number
  /** 宽(米) */
  width: number
  /** 层高(米) */
  height: number
  wallCount?: number
  openings?: Opening[]
  areas?: LayoutAreas
}

/** 结构化「户型数据模型」。§7.3、§8.3 */
export interface LayoutModel {
  /** 至少 1 个空间(minItems: 1) */
  spaces: LayoutSpace[]
}

/** 部位 → 瓷砖的选定。§5.2.4、§8.4 */
export interface PartSelection {
  part: Part
  sku: string
  /** 该部位面积(㎡)。服务端可依户型计算,前端可不传 */
  area?: number
  /** 响应中必填,**请求中可省略**(默认 user) */
  source?: PartSource
}

/** 户型模板。§8.3 */
export interface UnitTemplate {
  id: number
  name: string
  /** 居室数 */
  roomCount?: number
  /** 面积区间,例:80-100㎡ */
  areaRange?: string
  /** 户型结构,例:三室两厅 */
  structure?: string
  /** ⚠️ **可选** —— 模板未必配了户型,前端要有兜底文案 */
  layout?: LayoutModel
  status?: ProductStatus
}

/** 任务状态机。§6.4 */
export type TaskStatus = 'pending' | 'running' | 'success' | 'failed'

/**
 * 方案效果图。§8.4
 *
 * **张数 = `angles`**(不是 angles+1)。第 1 张 `view = default` 且 `isCover = true`;
 * 第 k 张(`k ≥ 2`)为 `angle{k-1}`。展示时**按 `view` 区分,不要靠数组下标猜**。
 */
export interface PlanImage {
  url: string
  view?: string
  isCover?: boolean
}

/**
 * 方案部位—瓷砖映射 + 报价分项。§8.4 t_plan_part
 *
 * ⚠️ **契约把 `materialFee` 标成 required,实现却会把它整个省略。**
 * 后端 `PlanPartView` 带 `@JsonInclude(NON_NULL)`:报价算不出来时(面积未定 /
 * 参数没配 / 产品下架)这个值是 null,JSON 里就**根本没有这个键**。
 * 契约是照「报价一定算得出来」的理想情况写的,差记在 §41。
 *
 * 所以这里写成可选,**渲染一律 `?? '—'`** —— 不要 `?? 0`(那会显示成 ¥0)。
 */
export interface PlanPart {
  /** 恒为 floor / wall / local 三值(契约 T-9) */
  part: Part
  sku: string
  /** 生成时快照的产品名(不是产品库的当前值) */
  productName?: string
  /** 生成时快照单价(元/㎡) */
  pricePerSqm?: number
  /** 毛面积(同部位同砖合并后),㎡ */
  grossArea?: number
  /** 有效面积(扣开口后),㎡ */
  area?: number
  /** 损耗率,小数(0.06 = 6%) */
  lossRate?: number
  /** 该分项材料费(元)。**可能整个字段不在** —— 见上方说明 */
  materialFee?: number
  /** 该部位的砖是用户选的还是 AI 补的 */
  source?: PartSource
}

/**
 * 「我的方案」卡片信息。§5.6
 *
 * ⚠️ **契约只要求 `id` / `name` / `createdAt` 三个**,其余全部可省略 ——
 * 尤其是 `totalAmount`:**没有这个值表示"这一次没算出价",不是 0**。
 * 直接 `totalAmount ?? 0` 会让用户看到一张 ¥0 的报价单。
 */
export interface PlanSummary {
  id: number
  name: string
  createdAt: string
  /** 生成空间,空间字典 key(取值以后台字典为准) */
  space?: string
  /** 风格,风格字典 key */
  style?: string
  /** 封面效果图 URL。**可能没有值** —— 前端画自己的占位,不伪造 */
  coverImage?: string
  /** 报价总额。**缺失 = 没算出价,不是 0** */
  totalAmount?: number
  /** 是否因产品调价而过期。§15.7 */
  quoteExpired?: boolean
  /** 所选瓷砖摘要 */
  parts?: PlanPart[]
  updatedAt?: string
}

/**
 * 方案详情。§5.6、§11.4
 *
 * 契约是 `allOf: [PlanSummary, {...4 个字段}]`,所以这里用继承表达同一件事。
 *
 * ⚠️ **`layout` 是解密后的明文** —— 库里存的是 AES-256-GCM 密文(`layout_enc`),
 * 但对外 schema 只有明文,`layout_enc` 永远不出现(契约校验表 T-8、CLAUDE.md)。
 */
export interface PlanDetail extends PlanSummary {
  /** 户型快照,**明文** */
  layout?: LayoutModel
  /** 报价汇总,与 `parts` 里的分项同源。**没算出价时整个省略** */
  quote?: QuoteResult
  images?: PlanImage[]
  /** 分享令牌;未生成分享时省略(T-1.13) */
  shareToken?: string
}

/** 报价分项(按部位)。§8.4、§10.6⑤ */
export interface QuoteItem {
  part: Part
  sku: string
  /** 毛面积(同部位同砖合并后) */
  grossArea: number
  /** 有效面积(扣开口后) */
  effectiveArea: number
  /** 损耗率,小数 */
  lossRate: number
  /** 该砖单价快照(元/㎡) */
  pricePerSqm: number
  /** 该分项材料费 */
  materialFee: number
}

/** 报价结果(单空间)。§5.5、§8.8 */
export interface QuoteResult {
  /** 材料费合计 */
  materialTotal: number
  /** 辅料费合计 */
  auxTotal: number
  /** 单空间总价 */
  total: number
  items: QuoteItem[]
  /** 生成时参数快照,用于追溯与「按当前价重算」。§8.8、§15.7 */
  rulesSnapshot?: {
    auxUnitPrice?: number
    lossRate?: number
  }
}

/**
 * 以下常量**不是**给业务用的,是给 `tests/contract.spec.ts` 核对契约用的。
 *
 * TypeScript 的类型在运行期不存在,没法拿 `interface` 去和契约 YAML 比。
 * 把这些"前端对契约的假设"显式写成运行时常量,测试才能断言它们没漂 ——
 * 否则契约改了、前端没跟上,要等到线上某个字段是 undefined 才发现。
 * **改这些常量之前先看契约。**
 */

/** 契约 ApiResponse.required。前端解包时依赖这三个一定有值 */
export const API_RESPONSE_REQUIRED_FIELDS = ['code', 'message', 'traceId'] as const

/** 契约 PageData.required。列表页依赖这四个齐备 */
export const PAGE_DATA_REQUIRED_FIELDS = ['list', 'total', 'page', 'size'] as const

/** 契约 Part.enum。一期只有三个部位,不含美缝 */
export const PART_VALUES = ['floor', 'wall', 'local'] as const

/**
 * 契约 PlanSummary.required。**只有这三个** —— 清单里少一个就是把可选字段
 * 当必填用(会在某个方案上读到 undefined);多一个就是把必填当可选,
 * 用户可能看到空白。上面 `PlanSummary` 的可选/必填划分以它为准。
 */
export const PLAN_SUMMARY_REQUIRED_FIELDS = ['id', 'name', 'createdAt'] as const

/**
 * 契约 PlanPart.required。
 *
 * ⚠️ 注意 `materialFee` **在契约里是必填,实现却会省略它**(见 `PlanPart` 的说明)。
 * 这个常量记录的是**契约现在的样子**,不是前端该照抄的形状 ——
 * 前端仍必须把 `materialFee` 当可选渲染。两者的差是登记在案的分歧,不是笔误。
 */
export const PLAN_PART_REQUIRED_FIELDS = ['part', 'sku', 'materialFee'] as const

/** 契约 QuoteResult.required。明细四项齐备才画得出报价单 */
export const QUOTE_RESULT_REQUIRED_FIELDS = ['materialTotal', 'auxTotal', 'total', 'items'] as const

