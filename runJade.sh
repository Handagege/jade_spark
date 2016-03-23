#!/bin/sh

#hadoop dfs -rmr /dw_ext/recmd/zhanghan/spfield/car/origin_clique
hadoop dfs -rmr /dw_ext/recmd/zhanghan/ebusiness2_clique

/data0/spark-1.3.0/bin/spark-submit --class zhanghan.jade \
		--master yarn-client \
		--jars /usr/local/hadoop-2.4.0/share/hadoop/common/lib/hadoop-lzo-cdh4-0.4.15-gplextras.jar \
		--executor-memory 4G \
		--num-executors 200 \
		--driver-memory 2G \
		target/jade-0.1-SNAPSHOT.jar \
		/dw_ext/recmd/zhanghan/ebusiness2_duplexRel /dw_ext/recmd/zhanghan/ebusiness2_clique \
		12 100 0.3 0.95 200
#/dw_ext/recmd/zhanghan/spfield/car/car_duplexRel /dw_ext/recmd/zhanghan/spfield/car/origin_clique \
				
