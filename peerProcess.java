import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.lang.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class peerProcess {
	public int currentPeerID;
	public int preferredNeighborNm;
	public int unchokeInterval;
	public int opUnchokeInterval;
	public String fileName;
	public int fileSize;
	public int pieceSize;
    public int pieceNm;
    
    public CopyOnWriteArrayList<peer> peerList = new CopyOnWriteArrayList<>();
	public HashMap<Integer, peerInfo> allPeerInfo = new HashMap<Integer,peerInfo>();
	public CopyOnWriteArrayList<Integer> interestList = new CopyOnWriteArrayList<Integer>();
	public CopyOnWriteArrayList<Integer> chokedList = new CopyOnWriteArrayList<Integer>();
	public CopyOnWriteArrayList<Integer> peerPool = new CopyOnWriteArrayList<Integer>();
	public CopyOnWriteArrayList<Integer> preferNbList =new CopyOnWriteArrayList<Integer> ();
	
	public ScheduledExecutorService exServiceCheckPeerFile;
	public ScheduledExecutorService exServicePreferredNeighbor;
	public ScheduledExecutorService exServiceOptUnchoke;
	
	boolean shutdown = false;
	
    peerFile pf;
	logWriting log;
	peer opNeighbor = null;
	
	public static void main(String[] args) {
        if(args.length != 1) {
            System.out.println("Please input a valid peer ID. (e.g. 1001)");
            return;
        }
        
        peerProcess pp = null;
        
        try {
            pp = new peerProcess(args[0]);
            startThreads(pp);
        }
        catch(Exception e){
        		e.printStackTrace();
        		return;
        }
        
        pp.exServiceCheckPeerFile.scheduleAtFixedRate(new CheckPeerFile(pp), 0, 1000, TimeUnit.MILLISECONDS);
        pp.exServicePreferredNeighbor.scheduleAtFixedRate(new choosePreferNb(pp), 0, pp.unchokeInterval, TimeUnit.SECONDS);
        pp.exServiceOptUnchoke.scheduleAtFixedRate(new chooseOpNb(pp), 0, pp.opUnchokeInterval, TimeUnit.SECONDS);
        
        while(true) {
    			try {
    				Thread.sleep(1000);
    			}
    			catch(Exception e) {
    				e.printStackTrace();
    			}
	        	
	    		if(!pp.shutdown)
	    			continue;
	    		
	    		pp.exServiceCheckPeerFile.shutdownNow();
	    		pp.exServicePreferredNeighbor.shutdownNow();
	    		pp.exServiceOptUnchoke.shutdownNow();
	    		
	    		try {
    				Thread.sleep(1000);
    			}
    			catch(Exception e) {
    				e.printStackTrace();
    			}
	    		
	    		if(!pp.exServiceCheckPeerFile.isTerminated())
	    			System.out.println("exServiceCheckPeerFile did not terminated.");
	    		if(!pp.exServicePreferredNeighbor.isTerminated())
	    			System.out.println("exServicePreferredNeighbor did not terminated.");
	    		if(!pp.exServiceOptUnchoke.isTerminated())
	    			System.out.println("exServiceOptUnchoke did not terminated.");	    		
	    		break;
        }
    }
	
	//check whether files have been all downloaded
	private static class CheckPeerFile implements Runnable {	
		peerProcess pp;
		
		CheckPeerFile(peerProcess pp) {
			this.pp = pp;
		}
		
		public void run() {
			boolean allExists = true;
			for (int i = 0; i < pp.peerPool.size(); i++) {
				if (!pp.allPeerInfo.get(pp.peerPool.get(i)).exist) {
					allExists = false;
					break;
				}
			}
			
			if(allExists) {
				pp.shutdown = true;
				//System.out.println("Shutting down.");
				for (int i = 0; i < pp.peerPool.size(); i++) {
					try {
						pp.allPeerInfo.get(pp.peerPool.get(i)).pr.end = true;
						pp.allPeerInfo.get(pp.peerPool.get(i)).pr.skt.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	//save information of all the peers
	class peerInfo {
		final int ID;
		final String hostName;
		final int portName;
		boolean exist;
		BitSet bitField;
		boolean choked;
		boolean interested;
		peer pr;
		
		peerInfo (int id, String hostNm, int portNm, boolean exist) {
			ID = id;
			hostName = hostNm;
			portName = portNm;
			this.exist = exist;
			bitField = new BitSet(pieceNm);
			choked = true;
			interested = false;
			pr = null;
		}	
	}
	
	
	public peerProcess(String ID) throws Exception {
		exServiceCheckPeerFile = Executors.newSingleThreadScheduledExecutor();
		exServicePreferredNeighbor = Executors.newSingleThreadScheduledExecutor();
		exServiceOptUnchoke = Executors.newSingleThreadScheduledExecutor();
		
		currentPeerID = Integer.parseInt(ID);
		
		BufferedReader commoncfg = null;
        BufferedReader peerInfocfg = null;
        String[] line = null;
		
        commoncfg = new BufferedReader(new FileReader(new File("Common.cfg")));
        line = commoncfg.readLine().split(" ");
        preferredNeighborNm = Integer.parseInt(line[1]);
        line = commoncfg.readLine().split(" ");
        unchokeInterval = Integer.parseInt(line[1]);
        line = commoncfg.readLine().split(" ");
        opUnchokeInterval = Integer.parseInt(line[1]);
        line = commoncfg.readLine().split(" ");
        fileName = line[1];
        line = commoncfg.readLine().split(" ");
        fileSize = Integer.parseInt(line[1]);
        line = commoncfg.readLine().split(" ");
        pieceSize = Integer.parseInt(line[1]);
        pieceNm = (int)(Math.ceil((double)fileSize/pieceSize));
        commoncfg.close();
        
        peerInfocfg = new BufferedReader(new FileReader(new File("PeerInfo.cfg")));
        int id = -1;
        String hostNm = "";
        int portNm = -1;
        int exist = -1;
        
        ServerSocket seSocket = null;
		Socket client;
		
		int myport = 0;  
		String str = null;
		
		log	= new logWriting(currentPeerID);
		
		peerInfocfg = new BufferedReader(new FileReader(new File("PeerInfo.cfg")));
		while ((str = peerInfocfg.readLine()) != null) {
        		line = str.split(" ");
            id = Integer.parseInt(line[0]);
            hostNm = line[1];
            portNm = Integer.parseInt(line[2]);
            exist = Integer.parseInt(line[3]);
            peer p = null;
            peerPool.add(id);
            
            if(id == currentPeerID) {
				myport = portNm;
            }
            
			else if (id < currentPeerID) {
				try {
					client = new Socket(hostNm, portNm);
					client.setSendBufferSize(1024000);
					client.setReceiveBufferSize(1024000);
					//client.setSoTimeout(5000);
				}
				catch(Exception e) {
					client = null;
					e.printStackTrace();
				}

				System.out.print("Peer " + currentPeerID + " makes a connection to Peer " + id + "\n");
				p = new peer(currentPeerID, id, client, fileSize, pieceSize, pieceNm, this);
				
				synchronized(peerList) {
					peerList.add(p);
				}

				log.connect(id);
				
			}
			
			else if (id > currentPeerID) {
				if(seSocket==null){
					seSocket = new ServerSocket(myport);
					//seSocket.setSoTimeout(5000);
				}
				try {
					client = seSocket.accept();
					client.setSendBufferSize(1024000);
					client.setReceiveBufferSize(1024000);
					//client.setSoTimeout(5000);
				}
				catch(Exception e) {
					client = null;
					e.printStackTrace();
				}
				//System.out.print("Peer " + currentPeerID + " is listening on other peer connections...");
				p = new peer(currentPeerID, id, client, fileSize, pieceSize, pieceNm, this);
				synchronized(peerList) {
					peerList.add(p);
				}
				log.connected(id);
			}
            
            peerInfo tempPeer = new peerInfo(id, hostNm, portNm, exist > 0);
            tempPeer.pr = p;
    			allPeerInfo.put(id, tempPeer);
        }
		
        peerInfocfg.close();
        //seSocket.close();
        
        pf = new peerFile(this, currentPeerID, fileName, fileSize, pieceSize, pieceNm);
	}
	
	static void startThreads(peerProcess pp) throws Exception {
		for(peer p: pp.peerList) {     //start thread
			new Thread(p).start();
		} 
	}
	
	//choose optimistic neighbor function
	private static class chooseOpNb implements Runnable {
		peerProcess pp;
		
		chooseOpNb(peerProcess pp) {
			this.pp = pp;
		}
		public void run() {
			synchronized(pp.chokedList) {
				if(pp.opNeighbor!=null) {
					pp.opNeighbor.choke();
					pp.chokedList.add(pp.opNeighbor.neighborID);
					pp.opNeighbor = null;
				}
				synchronized(pp.peerList) {
					Collections.shuffle(pp.peerList);
				}
				for(peer p: pp.peerList) {
					synchronized(pp.interestList) {
						if(!p.unchokeState && pp.interestList.contains(p.neighborID)) {
							pp.opNeighbor = p;
							pp.opNeighbor.unchoke();
							synchronized(pp.chokedList) {
								for(int j = 0; j < pp.chokedList.size(); j++) {
									if(pp.chokedList.get(j) == pp.opNeighbor.neighborID)
										pp.chokedList.remove(j);
								}
							}
							
							try {
								pp.log.changeOptimisticNeighbor(pp.opNeighbor.neighborID);
							}
							catch(IOException e) {
								e.printStackTrace();
							}
							
							//System.out.println(pp.currentPeerID + "select " + pp.opNeighbor.neighborID + "as optimistic neighbor:" );
							p.unchokeState = true;
							return;				
						}
					}
				}
			}
		}
	}
	
	private static class choosePreferNb implements Runnable {
		private final peerProcess pp;
		
		choosePreferNb(peerProcess pp) {
			this.pp = pp;
		}
		
		public void run() {
			//if a peer has a complete file, it determines preferred neighbors randomly among neighbors interested in its data 
			pp.preferNbList.clear();
			if(pp.allPeerInfo.get(pp.currentPeerID).exist) {
				synchronized(pp.interestList) {
					for (int k = 0; (k< pp.preferredNeighborNm) && (pp.interestList.size() - k > 0); k++) {
						
						Random rnd = new Random();
						int index = rnd.nextInt(pp.peerList.size());
						while(!pp.interestList.contains(pp.peerList.get(index).neighborID)) {
							index = rnd.nextInt(pp.peerList.size());
						}
						//System.out.println(index);
						//System.out.println(peerList.get(index).neighborID + " is " + peerList.get(index).unchokeState);
						if(!pp.peerList.get(index).unchokeState) {
							pp.peerList.get(index).unchoke();
							synchronized(pp.chokedList) {
								for(int j=0; j < pp.chokedList.size(); j++) {
									if(pp.chokedList.get(j) == pp.peerList.get(index).neighborID)
										pp.chokedList.remove(j);
								}
							}
							//pp.preferNbList.add(pp.peerList.get(index).neighborID);
							//System.out.println(pp.preferNbList.size());
						}
						
						pp.preferNbList.add(pp.peerList.get(index).neighborID);
						System.out.println(pp.currentPeerID + " selected "+ pp.peerList.get(index).neighborID + " as preferred neighbor.");
					} 
				}
			}
			//if a peer does not have complete file, sort the downloading speed in previous interval and select top-speed neighbors
			else {   
				speedSort(pp.peerList);
				
				//preferred neighbor number is larger than neighbor peers
		    		if(pp.peerList.size() <= pp.preferredNeighborNm) {	
		    			for(peer p: pp.peerList) {
		    				if(pp.allPeerInfo.get(p.neighborID).interested && !p.unchokeState) {
		    					p.unchoke();
		    					synchronized(pp.chokedList) {
			    					for(int j=0; j < pp.chokedList.size(); j++) {
			    						if(pp.chokedList.get(j) == p.neighborID)
			    							pp.chokedList.remove(j);
			    					}
		    					}
		    					p.unchokeState = true;
		    					//pp.preferNbList.add(p.neighborID);
		    					//System.out.println(pp.currentPeerID + " unchoke "+ p.neighborID);
		    				}pp.preferNbList.add(p.neighborID);
		    			}
		    		}
		    		
		    		//neighbor peers are more than preferred neighbor number
		    		else {
		    			for(int i=0; i < pp.peerList.size(); i++) {
		    				// Unchoking
		    				if(i < pp.preferredNeighborNm) {
		    					//System.out.println(peerList.get(i).neighborID + " " + peerList.get(i).unchokeState + " " + interestList.contains(peerList.get(i).neighborID));
		    					synchronized(pp.interestList) {
			    					if(pp.peerList.get(i).unchokeState == false && pp.interestList.contains(pp.peerList.get(i).neighborID)){
			    						//System.out.println("Unchoking neighbors ");
			    						pp.peerList.get(i).unchoke();
			    						synchronized(pp.chokedList) {
				    						for(int j=0; j < pp.chokedList.size(); j++) {
					    						if(pp.chokedList.get(j) == pp.peerList.get(i).neighborID) {
					    							pp.chokedList.remove(j);
					    						}
					    					}
			    						}
			    						pp.peerList.get(i).unchokeState = true;
			    						//pp.preferNbList.add(pp.peerList.get(i).neighborID);
			    						//System.out.println(pp.currentPeerID + " unchoke "+ pp.peerList.get(i).neighborID);
			    					}
		    					}pp.preferNbList.add(pp.peerList.get(i).neighborID);
		    				}
		    				// Choking
		    				else {
		    					if(pp.peerList.get(i).unchokeState) {
		    						//System.out.println("Choking neighbors ");
		    						pp.peerList.get(i).choke();
		    						pp.chokedList.add(pp.peerList.get(i).neighborID);
		    						for(int j=0; j < pp.preferNbList.size(); j++) {
			    						if(pp.preferNbList.get(j) == pp.peerList.get(i).neighborID) {
			    							pp.preferNbList.remove(j);
			    						}
			    					}
		    						pp.peerList.get(i).unchokeState = false;
		    						//System.out.println(pp.currentPeerID + " choke "+ pp.peerList.get(i).neighborID);
		    					}
		    				 }
		    			  }	
		    			}
			}
			//if(preferNbList.size()!=0)
			try {
				pp.log.changePreferredNeighbors(pp.preferNbList);
				System.out.println(pp.preferNbList.size());
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	//sort neighbor peers by speed
	public static synchronized void speedSort (CopyOnWriteArrayList<peer> list) {	
		Comparator<peer> c = new Comparator<peer> () {
			double s1,s2;
			public int compare(peer p1, peer p2) {
				s1=p1.getSpeed();
				s2=p2.getSpeed();
				if(s1>s2)
					return 1;
				else if(s1<s2)
					return -1;
				else 
					return 0;
			}
		};
		Collections.sort(list,c);
	 }
	
	//tell(send message to) all neighbors that one peer has received a piece of file
	public void broadcast(int index) throws IOException {
		synchronized(peerList) {
			for(peer p : peerList)
				p.sendHaveMsg(index, p.outMsg);
		}
	}
}
