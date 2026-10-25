# Elasticsearch + IK 中文分词插件（商品搜索索引使用 ik_max_word / ik_smart）
# 插件版本必须与 ES 版本完全一致；升级 ES 时同步修改两处版本号。
FROM docker.elastic.co/elasticsearch/elasticsearch:8.14.3

# 网络受限时可先下载 zip，放到本目录后改为：
#   COPY elasticsearch-analysis-ik-8.14.3.zip /tmp/ik.zip
#   RUN bin/elasticsearch-plugin install --batch file:///tmp/ik.zip
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.14.3
