source ./config 
source ./lib/validate-config

mkdir -p ./output

for workload in update-heavy read-mostly read-only
do
	./YCSB/bin/ycsb run mongodb -P ./datasets/$dataset -P ./workloads/$workload -p mongodb.url=$url -p maxexecutiontime=$seconds -threads $threads -s -t > ./output/$dataset-${threads}threads-$workload
done
