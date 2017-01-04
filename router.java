import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class router {
	public static void main(String argv[]) throws Exception {
		int num_links;
		int Infinity = 2^32-1;
		int num_router=5;
		
		if(argv.length != 4){
			System.out.print("router.java expects 4 arguments\n");
			System.exit(0);
		}
		
		int router_id = Integer.parseInt(argv[0]);
		String address =argv[1];
		InetAddress nse_host = InetAddress.getByName(address);
		int nse_port = Integer.parseInt(argv[2]);
		int router_port = Integer.parseInt(argv[3]);
		PrintWriter output = new PrintWriter("router" + router_id + ".log", "UTF-8");
		
		byte[] receiveData = new byte[1024];
		
		DatagramSocket routerSocket = new DatagramSocket(router_port);
		
		RIB[] RIB_table = new RIB[num_router];
		for(int i=0; i<num_router; i++){
			if(i+1 == router_id){
				RIB_table[i] = new RIB();
				RIB_table[i].sendto = -1;
				RIB_table[i].cost = 0;
			}
			else {
				RIB_table[i] = new RIB();
				RIB_table[i].sendto = Infinity;
				RIB_table[i].cost = Infinity;
			}
		}
		
		// send INIT to nsc
		ByteBuffer init_bytebuffer = ByteBuffer.allocate(4);
		init_bytebuffer = init_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
		init_bytebuffer.putInt(router_id);
		DatagramPacket init_packet = new DatagramPacket(init_bytebuffer.array(), 4, nse_host, nse_port);
		routerSocket.send(init_packet);
		output.println("R"+ router_id + " sent INIT packet, router ID: " + router_id +"\n");
		
		// receive circuit_DB from nsc
		DatagramPacket circuitDB_packet = new DatagramPacket(receiveData,receiveData.length);
		routerSocket.receive(circuitDB_packet);
		ByteBuffer circuitDB_bytebuffer = ByteBuffer.wrap(receiveData);
		circuitDB_bytebuffer = circuitDB_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
		num_links = circuitDB_bytebuffer.getInt();
		circuit_DB circuitDB = new circuit_DB();
		circuitDB.nbr_link = num_links;
		output.println("R" + router_id + " received a circuit_DB," + num_links + " links:");
		for(int i=0; i<num_links; i++){
			circuitDB.links[i] = new link_cost();
			circuitDB.links[i].link = circuitDB_bytebuffer.getInt();
			circuitDB.links[i].cost = circuitDB_bytebuffer.getInt();
			output.println("link: " + circuitDB.links[i].link + " ,cost:" + circuitDB.links[i].cost);
		}
		
		ArrayList<pkt_LSPDU> database = new ArrayList<pkt_LSPDU>();
		for(int i=0; i<num_links; i++){
			pkt_LSPDU temp = new pkt_LSPDU();
			temp.sender = router_id;
			temp.router_id = router_id;
			temp.link_id = circuitDB.links[i].link;
			temp.cost = circuitDB.links[i].cost;
			database.add(temp);
		}
		print(output,database,RIB_table,router_id);
		
		// send hello packet to its neighbour
		for(int i=0; i<num_links; i++){
			ByteBuffer hello_bytebuffer = ByteBuffer.allocate(8);
			hello_bytebuffer = hello_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
			hello_bytebuffer.putInt(router_id);
			hello_bytebuffer.putInt(circuitDB.links[i].link);
			DatagramPacket hello_packet = new DatagramPacket(hello_bytebuffer.array(),8,nse_host,nse_port);
			routerSocket.send(hello_packet);
			output.println("send hello packet: via " + circuitDB.links[i].link+"\n");
		}
		
		while(true){
			DatagramPacket received_packet = new DatagramPacket(receiveData, receiveData.length);
			routerSocket.receive(received_packet);
			ByteBuffer received_bytebuffer = ByteBuffer.wrap(receiveData);
			received_bytebuffer = received_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
			
			// received hello packet from other router
			if(received_packet.getLength() == 8){
				int neighbour = received_bytebuffer.getInt();
				int via = received_bytebuffer.getInt();
				output.println("receive hello packet: R" + neighbour + " via link " + via + "\n");
				
				// update RIB table
				RIB_table[neighbour-1].sendto = neighbour;
				for(int i=0; i<num_links;i++){
					if(via == circuitDB.links[i].link){
						RIB_table[neighbour-1].cost = circuitDB.links[i].cost;
						break;
					}
				}
				
				// send LSPDU to all discovered neighbour 
				for(int i=0; i<database.size(); i++){
					ByteBuffer send_LSPDU = ByteBuffer.allocate(20);
					send_LSPDU = send_LSPDU.order(ByteOrder.LITTLE_ENDIAN);
					send_LSPDU.putInt(database.get(i).sender);
					send_LSPDU.putInt(database.get(i).router_id);
					send_LSPDU.putInt(database.get(i).link_id);
					send_LSPDU.putInt(database.get(i).cost);
					send_LSPDU.putInt(via);
					DatagramPacket LSPDU_packet = new DatagramPacket(send_LSPDU.array(),20,nse_host,nse_port);
					routerSocket.send(LSPDU_packet);
				}
			}
			
			// received a LSPDU packet
			else{
				boolean duplicate = false;
				pkt_LSPDU new_LSPDU = new pkt_LSPDU();
				new_LSPDU.sender = received_bytebuffer.getInt();
				new_LSPDU.router_id = received_bytebuffer.getInt();
				new_LSPDU.link_id = received_bytebuffer.getInt();
				new_LSPDU.cost = received_bytebuffer.getInt();
				new_LSPDU.via = received_bytebuffer.getInt();
				output.printf("received a LSPDU: sender %d, router_id %d, link_id %d, cost %d, via %d\n\n", new_LSPDU.sender, new_LSPDU.router_id, new_LSPDU.link_id, new_LSPDU.cost, new_LSPDU.via);
				
				for(int i=0; i<database.size(); i++){
					if(database.get(i).router_id == new_LSPDU.router_id && database.get(i).link_id == new_LSPDU.link_id){
						duplicate = true;
						break;
					}
				}
				
				if(duplicate==false){
					// add to the link state database
					database.add(new_LSPDU);
					
					//
					for(int i=0; i<num_links; i++){
						if(circuitDB.links[i].link != new_LSPDU.via){
							ByteBuffer send_LSPDU = ByteBuffer.allocate(20);
							send_LSPDU = send_LSPDU.order(ByteOrder.LITTLE_ENDIAN);
							send_LSPDU.putInt(router_id);
							send_LSPDU.putInt(new_LSPDU.router_id);
							send_LSPDU.putInt(new_LSPDU.link_id);
							send_LSPDU.putInt(new_LSPDU.cost);
							send_LSPDU.putInt(circuitDB.links[i].link);
							DatagramPacket LSPDU_packet = new DatagramPacket(send_LSPDU.array(),20,nse_host,nse_port);
							routerSocket.send(LSPDU_packet);
						}
					}
					
					// dijkstra algorithm
					for(int i=0; i<num_router; i++){
						if(i+1 == new_LSPDU.router_id){
							RIB_table[i].cost = Math.min(RIB_table[new_LSPDU.sender-1].cost + new_LSPDU.cost, RIB_table[i].cost);
							RIB_table[i].sendto = new_LSPDU.sender;
						}
					}
					print(output,database,RIB_table,router_id);
				}
			}
			output.flush();
		}
	}
	
	
	private static void print(PrintWriter output, ArrayList<pkt_LSPDU> currentDB, RIB[] RIB_table, int router_id) {
		int size = currentDB.size();
		int INF = 2^32-1;
		output.println("\nRouter #" + router_id);
		output.println("# Topology database");
		
		ArrayList<pkt_LSPDU> array1 = new ArrayList<pkt_LSPDU>();
		ArrayList<pkt_LSPDU> array2 = new ArrayList<pkt_LSPDU>();
		ArrayList<pkt_LSPDU> array3 = new ArrayList<pkt_LSPDU>();
		ArrayList<pkt_LSPDU> array4 = new ArrayList<pkt_LSPDU>();
		ArrayList<pkt_LSPDU> array5 = new ArrayList<pkt_LSPDU>();
		
		for(int i=0; i<size; i++){
			if(currentDB.get(i).router_id==1){
				array1.add(currentDB.get(i));
			}
			else if(currentDB.get(i).router_id==2){
				array2.add(currentDB.get(i));
			}
			else if(currentDB.get(i).router_id==3){
				array3.add(currentDB.get(i));
			}
			else if(currentDB.get(i).router_id==4){
				array4.add(currentDB.get(i));
			}
			else if(currentDB.get(i).router_id==5){
				array5.add(currentDB.get(i));
			}
		}
		
		if(array1.size()!=0){
			output.printf("R%d -> R1 nbr link %d\n", router_id, array1.size());
			for(int i=0; i<array1.size();i++){
				output.printf("R%d -> R1 link %d cost %d\n", router_id, array1.get(i).link_id, array1.get(i).cost);
			}		
		}
		if(array2.size()!=0){
			output.printf("R%d -> R2 nbr link %d\n", router_id, array2.size());
			for(int i=0; i<array2.size();i++){
				output.printf("R%d -> R2 link %d cost %d\n", router_id, array2.get(i).link_id, array2.get(i).cost);
			}		
		}
		if(array3.size()!=0){
			output.printf("R%d -> R3 nbr link %d\n", router_id, array3.size());
			for(int i=0; i<array3.size();i++){
				output.printf("R%d -> R3 link %d cost %d\n", router_id, array3.get(i).link_id, array3.get(i).cost);
			}		
		}
		if(array4.size()!=0){
			output.printf("R%d -> R4 nbr link %d\n", router_id, array4.size());
			for(int i=0; i<array4.size();i++){
				output.printf("R%d -> R4 link %d cost %d\n", router_id, array4.get(i).link_id, array4.get(i).cost);
			}		
		}
		if(array5.size()!=0){
			output.printf("R%d -> R5 nbr link %d\n", router_id, array5.size());
			for(int i=0; i<array5.size();i++){
				output.printf("R%d -> R5 link %d cost %d\n", router_id, array5.get(i).link_id, array5.get(i).cost);
			}		
		}
		
		output.println("\n# RIB");
		for (int i = 0; i < 5; i++) {
			if (i+1 == router_id) {
				output.printf("R%d -> R%d -> Locat, 0\n", router_id, router_id);
            }
            else {
            	int printrouter = i+1;
            	if (RIB_table[i].sendto == INF) {
            		output.printf("R%d -> R%d -> INF, INF\n", router_id, printrouter);
				}
				else {
					output.printf("R%d -> R%d -> R%d, %d\n", router_id, printrouter, RIB_table[i].sendto, RIB_table[i].cost);
				}
            }
		}
		output.println("\n");
	}
	
}


class pkt_HELLO {
	public int router_id; /* id of the router who sends the HELLO PDU */
	public int link_id; /* id of the link through which it is sent */
};

class pkt_LSPDU {
	public int sender; /* sender of the LS PDU */
	public int router_id; /* router id */
	public int link_id; /* link id */
	public int cost; /* cost of the link */
	public int via; /* id of the link through which the LS PDU is sent */
};

class pkt_INIT {
	public int router_id; /* id of the router that sends the INIT PDU */
};

class link_cost {
	public int link; /* link id */
	public int cost; /* associated cost */
};

class circuit_DB {
	public int nbr_link; /* number of links attached to a router */
	public link_cost[] links = new link_cost[5];
/* we assume that at most NBR_ROUTER links are attached to each router */
};

class RIB {
	public int sendto; /* send to router # in shortest path */
	public int cost; /* total cost to reach destination */
};
