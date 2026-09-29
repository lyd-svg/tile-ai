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

