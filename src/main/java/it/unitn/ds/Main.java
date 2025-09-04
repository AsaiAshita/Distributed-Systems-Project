package it.unitn.ds;

import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;



public class Main {

  public static Map<ActorRef, Integer> sortByValue(Map<ActorRef, Integer> map) {
    // Convert map entries to a list
    List<Map.Entry<ActorRef, Integer>> entryList = new ArrayList<>(map.entrySet());

    // Sort the list by value
    Collections.sort(entryList, (entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()));

    // Create a new LinkedHashMap to keep the sorted order
    Map<ActorRef, Integer> sortedMap = new LinkedHashMap<>();
    for (Map.Entry<ActorRef, Integer> entry : entryList) {
      sortedMap.put((entry.getKey()), entry.getValue());
    }

    return sortedMap;
  }

  public static ArrayList<ActorRef> Update_nodes(ActorRef act, int id, ArrayList<ActorRef> currentView, Map<ActorRef, Integer> id_ref_association) {
      ArrayList<ActorRef> dummyView = new ArrayList<>();
      dummyView.addAll(currentView);

      boolean insertion = false;
      int index = 0;
      while (!insertion && index < currentView.size()) {
          Integer nodeId = id_ref_association.get(currentView.get(index));
          if (nodeId != null && nodeId > id) {
              dummyView.add(index, act);
              insertion = true;
          }
          index = index + 1;
      }
      //this means that it is the node with the highest id, should be inserted at the
      //end of the view
      if (!insertion) {
          dummyView.add(act);
      }

      return dummyView;
  }

  public static void main(String[] args) throws InterruptedException {
    // Create an actor system
    final ActorSystem system = ActorSystem.create("DS_Project");
    
    ArrayList<ActorRef> nodes = new ArrayList<>();

    Map<ActorRef, Integer> id_ref_association = new HashMap<>();

    Config.init();

    //the following variable decides how many nodes the system starts with.
    //Change it in order to modify execution if you choose to modify N
    int number_of_initial_nodes = 5;

    if(number_of_initial_nodes < Config.N){
        System.out.println("Error: N is greater than the number of nodes in the system");
        System.exit(1);
    }

    //create all initial actors
    for(int j = 0; j<number_of_initial_nodes; j++){
      ActorRef node = system.actorOf(
              Actor.props(10*(j+1)),    // actor class
              "node_" + j     // the new actor name (unique within the system)
      );
      nodes.add(node);
      id_ref_association.put(node, (10*(j+1)));
    }

    id_ref_association = sortByValue(id_ref_association);

    // Create a set of values for the nodes. Here we assume N=3.
    // If number_of_initial_nodes is changed, please modify the code below
    // to fit for the change.
    ArrayList<Integer> values_to_assign = new ArrayList<>();
    ArrayList<Map<Integer, Pair<Integer, String>>> allValues = new ArrayList<>();
    for(int i=0; i<number_of_initial_nodes; i++){
        Map<Integer, Pair<Integer, String>> valuesNode = new HashMap<>();
        allValues.add(valuesNode); //we create a map of values for each node
    }
    for(int i=0; i<number_of_initial_nodes; i++){
        int position = i;
        values_to_assign.add((i*10)+4); //4, 14, 24...
        for(int j=0; j<Config.N; j++){
            allValues.get(position%number_of_initial_nodes).put((i*10)+4, Pair.of(1, "val" + ((i*10)+4)));
            position = position + 1;
        }
        Config.MOST_RECENT_VERSION.put((i*10)+4, 1);
        position = i;
        values_to_assign.add(((i+1)*10)-2); //8, 18, 28...
        for(int j=0; j<Config.N; j++){
            allValues.get(position%number_of_initial_nodes).put(((i+1)*10)-2, Pair.of(1, "val" + (((i+1)*10)-2)));
            position = position + 1;
        }
        Config.MOST_RECENT_VERSION.put(((i+1)*10)-2, 1);
    }

    /*
    System.out.println(values_to_assign);
    System.out.println(Config.MOST_RECENT_VERSION);
    for(int i=0; i<number_of_initial_nodes; i++){
        System.out.println(id_ref_association.get(nodes.get(i)));
        System.out.println(allValues.get(i));
    }
    */

    // Create clients.
    ArrayList<ActorRef> clients = new ArrayList<>();
    ActorRef client0 = system.actorOf(Client.props(0,nodes), "client_0");
    ActorRef client1 = system.actorOf(Client.props(1,nodes), "client_1");
    ActorRef client2 = system.actorOf(Client.props(2,nodes), "client_2");
    ActorRef client3 = system.actorOf(Client.props(3,nodes), "client_3");
    clients.add(client0);
    clients.add(client1);
    clients.add(client2);
    clients.add(client3);

    // Update the actors view and values stored
    for (int i = 0; i < nodes.size(); i++) {
        ActorRef node = nodes.get(i);
        node.tell(new Actor.UpdateView(nodes), ActorRef.noSender());
        node.tell(new Actor.SetValues(allValues.get(i)), ActorRef.noSender());
        node.tell(new Actor.SetIdAssociation(id_ref_association), ActorRef.noSender());
        node.tell(new Actor.SetClientsView(clients), ActorRef.noSender());
    }

    for(int i=0; i<clients.size(); i++){
      Config.FIFO.put(clients.get(i), new ArrayList<>());
      Config.VECTOR_CLOCK.put(clients.get(i), 0);
    }

    //Note we assume that at least
    //two nodes exist, as all the below operations will be carried out on keys 4,8,14,18
    //and node 0,1 for simplicity's sake and to show that the system works.

    //Simulate reads on the same key by different clients and the write operations on
    //non-conflicting keys. This also shows FIFO-ness of the operations (please note that
    //the insertion of network delays may mess up the order the messages are shown. Disable
    //these delays if you want to have a definitive proof the system is FIFO-consistent
    System.out.println("=== STARTING SIMULATION ===");
    System.out.println("-- Simulate an execution of some update and get operations --");
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.GetMsg(4), client2);
    client3.tell(new Client.GetMsg(14), client3);
    client0.tell(new Client.GetMsg(14), client0);
    client1.tell(new Client.GetMsg(14), client1);
    TimeUnit.SECONDS.sleep(5);
    client2.tell(new Client.UpdateMsg(4, "Canada"), client2);
    client3.tell(new Client.UpdateMsg(14, "Italia"), client3);
    client0.tell(new Client.UpdateMsg(8, "Austria"), client0);
    TimeUnit.SECONDS.sleep(5);

    System.out.println("-- Simulation to test for FIFO-ness and sequential consistency --");
    //We simulate writes and reads to show the system is sequentially consistent and, thus, also
    //FIFO-consistent. We carry out various conflicting operations and show how the system
    //is able to reject operations that might break sequential consistency, thus succeeding in
    //achieving it
    client2.tell(new Client.UpdateMsg(4, "UpdateA"), client2);
    client2.tell(new Client.UpdateMsg(4, "UpdateB"), client2);
    client2.tell(new Client.UpdateMsg(4, "UpdateC"), client2);
    client1.tell(new Client.UpdateMsg(4, "DifferentUpdate"), client1);
    client0.tell(new Client.UpdateMsg(4, "AnotherUpdate"), client0);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.GetMsg(4), client2);
    TimeUnit.SECONDS.sleep(5);
    client2.tell(new Client.GetMsg(4), client2);
    TimeUnit.SECONDS.sleep(5);

    System.out.println("-- Crash and Recovery simulation --");
    // Crash and Recovery simulation without anything happening
    nodes.get(1).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    nodes.get(1).tell(new Actor.RecoveryMsg(nodes.get(0)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(10);

    System.out.println("-- Simulation of activities with a crashed node --");
    //we simulate operations while a node has crashed
    nodes.get(0).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Costa Rica"), client2);
    client3.tell(new Client.UpdateMsg(8, "Giappone"), client3);
    TimeUnit.SECONDS.sleep(6);
    nodes.get(0).tell(new Actor.RecoveryMsg(nodes.get(1)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(10);

    System.out.println("-- Simulation of operations with multiple crashed nodes");
    //we simulate operations while multiple nodes are crashed
    nodes.get(0).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    nodes.get(1).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Lettonia"), client2);
    client3.tell(new Client.UpdateMsg(8, "Lituania"), client3);
    TimeUnit.SECONDS.sleep(5);
    client0.tell(new Client.GetMsg(14), client0);
    client1.tell(new Client.UpdateMsg(18, "CoreaDelSud"), client1);
    TimeUnit.SECONDS.sleep(5);
    nodes.get(1).tell(new Actor.RecoveryMsg(nodes.get(2)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(4);
    nodes.get(0).tell(new Actor.RecoveryMsg(nodes.get(2)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(10);

    System.out.println("-- Simulation to test the Join --");
    //Simulation to test the Join
    int node_id = 15;
    ActorRef node = system.actorOf(
              Actor.props(node_id),
              "node_15"
      );
    node.tell(new Actor.JoinMsg(node_id, nodes.get(0)), ActorRef.noSender());
    //we update the main view of the system, so to also be able to issue it leave/crash operations
    //if needed
    nodes = Update_nodes(node, node_id, nodes, id_ref_association);
    id_ref_association.put(node, node_id);
    id_ref_association = sortByValue(id_ref_association);
    TimeUnit.SECONDS.sleep(10);
    client3.tell(new Client.UpdateMsg(14, "NewValAfterJoin"), client3);
    client0.tell(new Client.GetMsg(14), client0);
    TimeUnit.SECONDS.sleep(5);
    client0.tell(new Client.GetMsg(14), client0);
    client2.tell(new Client.UpdateMsg(8, "Honduras"), client2);
    client3.tell(new Client.UpdateMsg(4, "FaroeIslands"), client3);
    TimeUnit.SECONDS.sleep(5);

    System.out.println("-- Simulation to test the Leave --");
    // Simulate leave operation
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(5);
    nodes.get(0).tell(new Actor.LeaveMsg(id_ref_association.get(nodes.get(0)), nodes.get(0)), ActorRef.noSender());
    id_ref_association.remove(nodes.get(0));
    nodes.remove(nodes.get(0));
    TimeUnit.SECONDS.sleep(10);
    client0.tell(new Client.GetMsg(4), client0);
    client2.tell(new Client.UpdateMsg(14, "Ciad"), client2);
    client3.tell(new Client.UpdateMsg(8, "Madagascar"), client3);
    TimeUnit.SECONDS.sleep(5);

    System.out.println("-- Final Simulation with everything at once --");
    node_id = 25;
    node = system.actorOf(
            Actor.props(node_id),
            "node_25"
    );
    node.tell(new Actor.JoinMsg(node_id, nodes.get(1)), ActorRef.noSender());
    nodes = Update_nodes(node, node_id, nodes, id_ref_association);
    id_ref_association.put(node, node_id);
    id_ref_association = sortByValue(id_ref_association);
    TimeUnit.SECONDS.sleep(10);
    nodes.get(0).tell(new Actor.LeaveMsg(id_ref_association.get(nodes.get(0)), nodes.get(0)), ActorRef.noSender());
    id_ref_association.remove(nodes.get(0));
    nodes.remove(nodes.get(0));
    TimeUnit.SECONDS.sleep(10);
    nodes.get(0).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    client0.tell(new Client.GetMsg(4), client0);
    client0.tell(new Client.UpdateMsg(14, "Spagna"), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client1.tell(new Client.UpdateMsg(4, "RepubblicaCeca"), client1);
    client2.tell(new Client.GetMsg(14), client2);
    client2.tell(new Client.UpdateMsg(8, "Galles"), client2);
    client3.tell(new Client.GetMsg(18), client3);
    client3.tell(new Client.UpdateMsg(18, "Svezia"), client3);
    TimeUnit.SECONDS.sleep(10);
    nodes.get(0).tell(new Actor.RecoveryMsg(nodes.get(2)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    System.out.println("\n=== SIMULATION COMPLETED ===");

    System.out.println(">>> Press ENTER to exit <<<");
    try {
      System.in.read();
    }
    catch (IOException ioe) {}
    finally {
      system.terminate();
    }
  }
}
