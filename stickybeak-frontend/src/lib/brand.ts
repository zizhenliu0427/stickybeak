/**
 * 品牌对外链接。小红书用用户 ID 路径，不用 xhslink 短链、也不带 xsec_token
 * （后两者都会过期；profile ID 是稳定主键）。
 */
export const GDCUP_XHS = {
  nickname: 'GDCUP',
  redId: '960649927',
  profileUrl: 'https://www.xiaohongshu.com/user/profile/5c56ecb600000000180107b0',
} as const;

/** 悉尼线下合作店（帖子原文：Sydney souvenirs / Paddy's markets Haymarket 460 号） */
export const SYDNEY_SOUVENIRS_XHS = {
  nickname: 'Sydney Souvenirs',
  profileUrl: 'https://www.xiaohongshu.com/user/profile/6473e84b000000000f005a1e',
} as const;

export const PADDYS_MARKET = {
  name: "Paddy's Markets",
  partner: 'Sydney Souvenirs',
  stall: '460',
  suburb: 'Haymarket',
  mapsUrl:
    "https://maps.google.com/?q=Sydney+Souvenirs+Stall+460+Paddy%27s+Markets+Haymarket+Sydney",
} as const;
