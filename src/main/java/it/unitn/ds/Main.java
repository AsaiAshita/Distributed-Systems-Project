package it.unitn.ds;

import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

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

  public static void main(String[] args) throws InterruptedException {
    // Create an actor system
    final ActorSystem system = ActorSystem.create("DS_Project");
    
    ArrayList<ActorRef> nodes = new ArrayList<>();

    Map<ActorRef, Integer> id_ref_association = new HashMap<>();

    //initial values we will give to the starting set of nodes
    int [] initial_values = {10,20,30,40,50};

    Config.init();

    //create all initial actors
    for(int j = 0; j<5; j++){
      ActorRef node = system.actorOf(
              Actor.props(initial_values[j]),    // actor class
              "node_" + j     // the new actor name (unique within the system)
      );
      nodes.add(node);
      id_ref_association.put(node, initial_values[j]);
    }

    id_ref_association = sortByValue(id_ref_association);
    //System.out.println(id_ref_association);
    //System.out.print(nodes);

    // Create a set of values for the nodes. Here we assume N=3.
    // However, per the project, N should be configurable at compile time, so
    // this part needs some rework to be able to do it
    Map<Integer, Pair<Integer, String>> valuesNode0 = new HashMap<>();
    valuesNode0.put(4, Pair.of(1, "val4"));
    valuesNode0.put(9, Pair.of(1, "val9"));
    valuesNode0.put(45, Pair.of(1, "val45"));

    Map<Integer, Pair<Integer, String>> valuesNode1 = new HashMap<>();
    valuesNode1.put(4, Pair.of(1, "val4"));
    valuesNode1.put(9, Pair.of(1, "val9"));
    valuesNode1.put(11, Pair.of(1, "val11"));
    valuesNode1.put(45, Pair.of(1, "val45"));

    Map<Integer, Pair<Integer, String>> valuesNode2 = new HashMap<>();
    valuesNode2.put(4, Pair.of(1, "val4"));
    valuesNode2.put(9, Pair.of(1, "val9"));
    valuesNode2.put(11, Pair.of(1, "val11"));
    valuesNode2.put(24, Pair.of(1, "val24"));
    valuesNode2.put(29, Pair.of(1, "val29"));

    Map<Integer, Pair<Integer, String>> valuesNode3 = new HashMap<>();
    valuesNode3.put(11, Pair.of(1, "val11"));
    valuesNode3.put(24, Pair.of(1, "val24"));
    valuesNode3.put(29, Pair.of(1, "val29"));

    Map<Integer, Pair<Integer, String>> valuesNode4 = new HashMap<>();
    valuesNode4.put(24, Pair.of(1, "val24"));
    valuesNode4.put(29, Pair.of(1, "val29"));
    valuesNode4.put(45, Pair.of(1, "val45"));

    ArrayList<Map<Integer, Pair<Integer, String>>> allValues = new ArrayList<>();
    allValues.add(valuesNode0);
    allValues.add(valuesNode1);
    allValues.add(valuesNode2);
    allValues.add(valuesNode3);
    allValues.add(valuesNode4);

    Config.MOST_RECENT_VERSION.put(4, 1);
    Config.MOST_RECENT_VERSION.put(9, 1);
    Config.MOST_RECENT_VERSION.put(11, 1);
    Config.MOST_RECENT_VERSION.put(24, 1);
    Config.MOST_RECENT_VERSION.put(29, 1);
    Config.MOST_RECENT_VERSION.put(45, 1);

    // Create clients
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

    System.out.println("=== STARTING SIMULATION ===");

    //Simulate an execution of some update and get operations
    System.out.println("-- Simulate an execution of some update and get operations --");
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.UpdateMsg(4, "FirstUpdate"), client1);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.GetMsg(4), client2);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Simulation to test for FIFO-ness and sequential consistency --");
    //Simulation to test for FIFO-ness and sequential consistency
    client2.tell(new Client.UpdateMsg(9, "UpdateA"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.UpdateMsg(9, "UpdateB"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.UpdateMsg(9, "UpdateC"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.GetMsg(9), client2);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Crash and Recovery simulation --");
    // Crash and Recovery simulation
    nodes.get(1).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    nodes.get(1).tell(new Actor.RecoveryMsg(nodes.get(0)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    System.out.println("-- Simulation to test the Join --");
    //Simulation to test the Join
    ActorRef node = system.actorOf(
            Actor.props(99),   
            "node_99" 
    );
    node.tell(new Actor.JoinMsg(99, nodes.get(0)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    client3.tell(new Client.UpdateMsg(11, "NewValAfterJoin"), client3);
    client0.tell(new Client.GetMsg(11), client0);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Simulation to test the Leave --");
    // Simulate leave operation
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
    nodes.get(3).tell(new Actor.LeaveMsg(3, nodes.get(3)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(3);
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Final consistency check --");
    // Final consistency check
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.GetMsg(45), client2);
    client3.tell(new Client.GetMsg(24), client3);
    TimeUnit.SECONDS.sleep(3);

    System.out.println("\n=== SIMULATION COMPLETED ===");

    //the following is a remnant of the lab files I took inspiration from for the basis of the project
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
