source ./config
source ./lib/validate-config
mkdir -p ./output

./YCSB/bin/ycsb load mongodb -P ./datasets/$dataset -p mongodb.url=$url -threads $threads -s > ./output/$dataset-${threads}threads-load
