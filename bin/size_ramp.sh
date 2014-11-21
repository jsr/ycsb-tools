name=${1-test}
source ./config
source ./lib/validate-config
outputdir=./output/$(date +%Y-%h-%d-%H:%M)-$name
mkdir -p $outputdir

echo "Starting run phase at $(date +%Y-%h-%d-%H:%M)" 

threads=64
seconds=120

currentcount=0
for size in 16000000 32000000 64000000 128000000
do
	let toinsert=size-currentcount
	dataset="-p workload=com.yahoo.ycsb.workloads.CoreWorkload -p recordcount=$size -p insertorder=ordered"
        loadparams="-p insertstart=${currentcount} -p insertcount=${toinsert}"

	./downloads/mongodb-linux-x86_64-2.6.0/bin/mongostat --noheaders --host $url --discover > $outputdir/${size}-load-mongostat & 
	PID=$!

	./YCSB/bin/ycsb load mongodb $dataset $loadparams -p mongodb.url=$url -threads $threads -s &> $outputdir/${size}-load  

	kill $PID

	currentcount=$size

	for workload in update-heavy read-mostly read-only
	do
		workload_name=$size-${threads}threads-$workload
		./downloads/mongodb-linux-x86_64-2.6.0/bin/mongostat --noheaders --host $url --discover > $outputdir/${workload_name}-mongostat & 
		PID=$!
		./YCSB/bin/ycsb run mongodb $dataset -P ./workloads/$workload -p mongodb.url=$url -p maxexecutiontime=$seconds -threads $threads -s -t &> $outputdir/$workload_name
		kill $PID
	done
done

echo "Done at $(date +%Y-%h-%d-%H:%M)" 

