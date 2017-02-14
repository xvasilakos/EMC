Produced with the GlobeTraff trafic genrator tool: 
  "GlobeTraff: a traffic workload generator for the performance evaluation of future Internet architectures"
  by K. Katsaros, G. Xylomenos and G. C. Polyzos
  5th International Conference on New Technologies, Mobility and Security (NTMS), pp. 1-5, 
  IEEE, 2012



Records in trace files follow the structure:

	docs.x.all:
		ID
		Popularity (in #Requests)
		Size (in #Bytes)
		AppType
	

	workload.x.all:
		Time of request	
		ID
		Size (in #Bytes)



docs.0.all:		avgSize=19950.6298731626	stdev=5951014.634161833		max=4293594969 min=4951
docs.1.all:		avgSize=12218.502779957947	 stdev=730053.2488681189	 max=396361728 min=4858
docs.2.all:		avgSize=12399.895463310175	 stdev=644209.9648904442	 max=518132518 min=4983
docs.3.all:		avgSize=11732.04062285011	 stdev=367119.3413264448	 max=154441573 min=4937
docs.4.all:		avgSize=11947.153171903918	 stdev=555293.4725093237	 max=298492102 min=4917
		
		
		
		
The workload consists of two files: 

(1) docs.all,  with tuples of the form: 

<ItemID> <Popularity(#requests)> <Size(Bytes)> <ApplicationType>

You dont need to do anything about the Size, since this is isnt about packet level simulation, and the ApplicationType as this is basically web traffic. The tool creates a traffic mix based on the sizes of the contents so you need a very large size to get a lot of videos and p2p items, so this is limited to Web which is fine I think.

(2) workload.all, with tuples of the form:

<Time> <ItemID> <Size(Bytes)>

Time is obviously  the timestamp of the request. I think for Icarus it suffices to just maintain the order. I have ordered the file in increasing timestamp order. 

The workload has the following characteristics:

- n contents: 201,767
- requests: 694267  in total, you can split it in a 2:7 warm up ratio as you did for your own workload
- content popularity Zipf distributed, alpha 0.9
- requests are distributed in time based on an LRU stack model. This actually determines the ordering of the request which is what matters in our case.		
