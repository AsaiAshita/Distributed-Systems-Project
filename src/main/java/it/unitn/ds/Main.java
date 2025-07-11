package it.unitn.ds;

import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;

 
public class Main {
  //final static int N_SENDERS = 5;

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
    System.out.print(id_ref_association);

    // Create a set of values for the nodes. Here we assume N=3.
    // However, per the project, N should be configurable at compile time, so
    // this part needs some rework to be able to do it
    Map<Integer, Pair<Integer, String>> valuesNode0 = new HashMap<>();
    valuesNode0.put(4, Pair.of(1, "val4"));
    valuesNode0.put(9, Pair.of(1, "val9"));
    valuesNode0.put(45, Pair.of(1, "val45"));


    Map<Integer, Pair<Integer, String>> valuesNode1 = new HashMap<>();
    valuesNode1.put(4, Pair.of(2, "val4"));
    valuesNode1.put(9, Pair.of(1, "val9"));
    valuesNode1.put(11, Pair.of(1, "val11"));
    valuesNode1.put(45, Pair.of(1, "val45"));



    Map<Integer, Pair<Integer, String>> valuesNode2 = new HashMap<>();
    valuesNode2.put(4, Pair.of(1, "val4"));
    valuesNode2.put(9, Pair.of(1, "val9"));
    valuesNode2.put(11, Pair.of(1, "val11"));
    valuesNode2.put(24, Pair.of(2, "val24"));
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

    // Update the actors view and values stored
    for (int i = 0; i < nodes.size(); i++) {
        ActorRef node = nodes.get(i);
        node.tell(new Actor.UpdateView(nodes), ActorRef.noSender());
        node.tell(new Actor.SetValues(allValues.get(i)), ActorRef.noSender());
        node.tell(new Actor.SetIdAssociation(id_ref_association), ActorRef.noSender());
    }

    // Create a client
    ActorRef client0 = system.actorOf(Client.props(0,nodes), "client_0");
    ActorRef client1 = system.actorOf(Client.props(1,nodes), "client_1");
    ActorRef client2 = system.actorOf(Client.props(2,nodes), "client_2");
    ActorRef client3 = system.actorOf(Client.props(3,nodes), "client_3");

    //Simulate an execution of some update and get operations.
    //They were written randomly, so they may not be the best when testing the system.
    //However, for the time being, it illustrates whether the system works or not.
    client1.tell(new Client.GetMsg(4), client1);
    client3.tell(new Client.GetMsg(9), client3);

    client0.tell(new Client.UpdateMsg(11, "Antananarivo"), client0);
    client1.tell(new Client.UpdateMsg(4, "La Paz"), client1);
    client1.tell(new Client.UpdateMsg(9, "Victoria"), client1);
    client2.tell(new Client.GetMsg(9), client2);
    client3.tell(new Client.UpdateMsg(45, "Lumezzane"), client3);
    client3.tell(new Client.UpdateMsg(24, "Oslo"), client3);

    TimeUnit.SECONDS.sleep(4);

    client0.tell(new Client.GetMsg(11), client0);
    client0.tell(new Client.UpdateMsg(11, "America"), client0);
    client1.tell(new Client.UpdateMsg(45, "Perugia"), client1);
    client1.tell(new Client.UpdateMsg(4, "Sendai"), client1);
    client3.tell(new Client.GetMsg(45), client3);
    client2.tell(new Client.GetMsg(24), client2);

    TimeUnit.SECONDS.sleep(2);

    client0.tell(new Client.GetMsg(24), client0);

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
