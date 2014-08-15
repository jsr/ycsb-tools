print("Dropping YCSB database"); 
db.getSiblingDB("ycsb").dropDatabase();
if( db.serverStatus().process == "mongos" ) { 
	print("Cluster is sharded. Initializing replication"); 
	sh.enableSharding("ycsb");
	sh.shardCollection("ycsb.usertable",{"_id":'hashed'});
	db.getSiblingDB("ycsb").runCommand( {"collMod" : "usertable" , "usePowerOf2Sizes" : false } );
	sh.setBalancerState(false); 
	sh.waitForBalancerOff();
} else { 
	print("Cluster is not sharded. Nothing more to do"); 
} 
print("done");
