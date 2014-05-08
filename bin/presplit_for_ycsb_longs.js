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

// YCSB generates random longs in java which have 2^63-1 as max
var MAX_VALUE = Math.pow(2,63) - 1; 
var CHUNKS_PER_SHARD = 20;
var step = MAX_VALUE/(numShards*CHUNKS_PER_SHARD); 

for(i = 0; i < MAX_VALUE; i=i+step) { 
   print("Chunk " + i);
   result = admin.runCommand({split: 'ycsb.usertable', middle: { _id: i }});
   printjson(result);
}

chunks = db.getSiblingDB("config").chunks.find();
shardid = 0;
while(chunks.hasNext()) { 
   chunk = chunks.next();
   destinationShard = shardNames[shardid++ % numShards];
   print("Moving chunk: " + chunk._id);
   result = sh.moveChunk(ns, {_id: chunk.min._id}, destinationShard );  
}

sh.enableBalancing("ycsb.usertable");

db.getSiblingDB("ycsb").runCommand( {"collMod" : "usertable" , "powerOf2Sizes" : false } );
