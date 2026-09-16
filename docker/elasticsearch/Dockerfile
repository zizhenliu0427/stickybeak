# Elasticsearch 8.14.3 + IK 中文分词插件（商品搜索用）
# 插件装在镜像层：容器重建不丢（es-data 卷只持久化数据，不含 plugins 目录）
FROM docker.elastic.co/elasticsearch/elasticsearch:8.14.3
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.14.3
