#!/bin/sh

#hadoop dfs -rmr /dw_ext/recmd/zhanghan/spfield/design/design_duplexRel
#hadoop dfs -rmr /dw_ext/recmd/zhanghan/spfield/car/car_duplexRel
#hadoop dfs -rmr /dw_ext/recmd/zhanghan/spfield/home/home_duplexRel
#hadoop dfs -rmr /dw_ext/recmd/zhanghan/ebusiness2_duplexRel

/data0/spark-1.3.0/bin/spark-submit --class zhanghan.trans2duplexRel \
		--master yarn-client \
		--jars /usr/local/hadoop-2.4.0/share/hadoop/common/lib/hadoop-lzo-cdh4-0.4.15-gplextras.jar \
		--executor-memory 4G \
		--num-executors 10 \
		--driver-memory 2G \
		target/jade-0.1-SNAPSHOT.jar \
		/dw_ext/recmd/zhanghan/coworkerFellowRel.data /dw_ext/recmd/zhanghan/coworker_duplexRel
#		/dw_ext/recmd/zhanghan/ebusiness2_followRel.data /dw_ext/recmd/zhanghan/ebusiness2_duplexRel
#/dw_ext/recmd/zhanghan/spfield/car/car_FollowRel.data /dw_ext/recmd/zhanghan/spfield/car/car_duplexRel
#/dw_ext/recmd/zhanghan/spfield/design/design_FollowRel.data /dw_ext/recmd/zhanghan/spfield/design/design_duplexRel
#/dw_ext/recmd/zhanghan/spfield/home/home_FollowRel.data /dw_ext/recmd/zhanghan/spfield/home/home_duplexRel
				
