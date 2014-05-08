sh.enableSharding("ycsb")
sh.shardCollection("ycsb.usertable",{"_id":1});
sh.disableBalancing("ycsb.usertable");
db.printShardingStatus(true);

var ns = "ycsb.usertable";

var shards = db.getSiblingDB("config").shards.find();
var numShards = shards.length();
var shardNames = []; 

for( var s = 0; s < numShards; s++ ) { 
    shardNames.push( shards[s]._id )
}

var admin = db.getSiblingDB("admin");
for (i = 0; i < 1000; i=i+5) { // split points at 0, 4, 9, 14, .. 999 
    prefix = "user" + String(i);
    print("Chunk " + prefix);
    result = admin.runCommand( {split: 'ycsb.usertable', middle: { _id: prefix }} );
    printjson(result);
}

print("Moving chunks ...");
for (i = 0; i < 1000; i=i+5) {
    prefix = "user" + String(i);
    var destinationShard = shardNames[i % numShards];
    print("Moving chunk: " + prefix + " to shard " + destinationShard);
    result = sh.moveChunk(ns, {_id: prefix}, destinationShard );
    printjson(result);
}

db.getSiblingDB("ycsb").runCommand( {"collMod" : "usertable" , "usePowerOf2Sizes" : false } );

sh.enableBalancing("ycsb.usertable");
