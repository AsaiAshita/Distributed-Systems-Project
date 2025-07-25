package it.unitn.ds;

import akka.actor.*;

import java.io.Serializable;
import java.util.*;

import akka.routing.ActorRefRoutee;
import org.apache.commons.lang3.tuple.Pair;

import it.unitn.ds.Client.GetMsg;
import it.unitn.ds.Client.UpdateMsg;
import it.unitn.ds.Client.UpdateClientView;

public class Actor extends AbstractActor {

    // Node identifier
    private int id;

    // Logical Clock, used to identify each request for a client
    private int clock = 0;

    // Number of nodes storing the replica
    // Fattore di replica configurabile
    private static final int N = Config.N;

    // Read quorum - we generalize it to N/2 + 1, as we need W + R > N
    private static final int R = Config.R;

    // Write quorum - we generalize it to N/2 + 1, as we need W > N/2 and W + R > N
    private static final int W = Config.W;

    // List of other nodes in the system (excluding self)
    private ArrayList<ActorRef> currentView;

    // Local key-value store: key -> (version, value)
    private Map<Integer, Pair<Integer,String>> values;

    // Keeps track of the responses received for each read request
    private Map<String, ArrayList<Pair<Integer,String>>> pendingReads = new HashMap<>();

    // Keeps track of the reads issued by a Join operation - separated from PendingReads for simplciity's sake
    private Map<Integer, ArrayList<Pair<Integer,String>>> pendingInternalReads = new HashMap<>();

    // Maps each read key to the client actor that requested it
    private Map<String, ActorRef> pendingClients = new HashMap<>();

    // Maps each Actor in the system to its id
    private Map<ActorRef, Integer> id_ref_association = new HashMap<>();

    // Used for introducing randomized delays in responses
    private final Random random = new Random();

    // Timeout duration for quorum wait
    private static final int TIMEOUT_MS = Config.TIMEOUT_MS;

    //Set containing all the requests - used to preserve FIFO assumption
    //This is due to random delays possibly breaking it
    private ArrayList<String> fifo = new ArrayList<>();

    // List of clients
    private ArrayList<ActorRef> clients = new ArrayList<>();

    public Actor(int id) {
        this.id = id;
        this.currentView = new ArrayList<>();
        this.values = new HashMap<>();
    }

    public static Props props(int id) {
        return Props.create(Actor.class, () -> new Actor(id));
    }

     // Handles a Get request from a client. If no read is pending for the key, initiate a quorum read.
    private void getValue(GetMsg getMsg){
        //a process can issue multiple reads and writes even on the same key, thus this part
        //is not necessary
        //if (pendingReadOperations.contains(getMsg.key)) {
        //    return;
        //}
        clock = clock + 1;
        //System.out.println(clock + " for " + getSelf());
        String request_id = getMsg.key + "id" + this.id + "c" + clock;

        //pendingReadOperations.add(getMsg.key);
        pendingReads.put(request_id, new ArrayList<>());
        pendingClients.put(request_id, getSender());
        fifo.add(request_id);

        // Send InternalGetMsg to all nodes in the view
        // Incorrect! Only need to send to the N nodes that have the value!
        //for (int i = 0; i < currentView.size(); i++) {
        //    currentView.get(i).tell(new Actor.InternalGetMsg(getMsg.key), getSelf());
        //}

        final ArrayList<ActorRef> nodesForGet = new ArrayList<>();

        //find the nodes to which we need to send the request to
        //here for the moment we assume that the nodes are stored in the hashmap in id order.
        //This may be false, however, hence we need to preserve this when we
        //update the view
        for(int i =0; i<currentView.size(); i++){
            if (id_ref_association.get(currentView.get(i)) >= getMsg.key && nodesForGet.size() < N){
                nodesForGet.add(currentView.get(i));
            }
        }
        if (nodesForGet.size() != N){
            int temp = nodesForGet.size();
            for(int i=0; i<N-temp; i++){
                nodesForGet.add(currentView.get(i));
            }
        }

        for (int i = 0; i < nodesForGet.size(); i++) {
            nodesForGet.get(i).tell(new Actor.InternalGetMsg(getMsg.key, request_id), getSelf());
        }

        // Send to the coordinator as well
        // Note: it's useless, because the coordinator itself should be in the view
        // hence if it has the value it will automatically deal with it due to the
        // mechanism implemented above
        // getSelf().tell(new Actor.InternalGetMsg(getMsg.key), getSelf());

        // Schedule a timeout in case not enough responses arrive in time
        getContext().getSystem().scheduler().scheduleOnce(
            scala.concurrent.duration.Duration.create(TIMEOUT_MS, "milliseconds"),
            getSelf(),
            new Timeout(getMsg.key, request_id),
            getContext().getDispatcher(),
            ActorRef.noSender()
        );
    }

    // Handles an InternalGetMsg by replying with the local value after a random delay.
    private void handleInternalGet(InternalGetMsg msg) {
        if (values.containsKey(msg.key)) {
            //System.out.println("Actor. " + getSelf() + " recevied message for key " + msg.key);
            Pair<Integer, String> pair = values.get(msg.key);
            int delayMs = 100 + random.nextInt(2901); // Delay between 100ms and 3000ms

            ActorRef originalSender = getSender();
            if(!msg.flag){
                getContext().getSystem().scheduler().scheduleOnce(
                        scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                        () -> originalSender.tell(new Actor.ReceiveMsg(msg.key, pair.getLeft(), pair.getRight(), msg.request), getSelf()),
                        getContext().getDispatcher()
                );
            }
            else{
                getContext().getSystem().scheduler().scheduleOnce(
                        scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                        () -> originalSender.tell(new Actor.ReceiveMsg(msg.key, pair.getLeft(), pair.getRight(), true), getSelf()),
                        getContext().getDispatcher()
                );
            }
        }
    }


    // Handles ReceiveMsg replies from nodes. If enough responses are received, selects the one with the highest version and returns it to the client.
    private void receiveResponses(ReceiveMsg msg) {
        //we divide the get into two modes
        //1. client request: the flag was never set, we proceed as asked to process the request from the client. In this case, we
        //wait for the quorum to be reached. When it is and the operation should proceed, we get the most up to date
        //version of the data and send it to the client, then we remove the elements from the Read map related to that key.
        if(!msg.flag){
            pendingReads.get(msg.request).add(Pair.of(msg.version, msg.value));
            if (pendingReads.get(msg.request).size() >= R) {
                //if there is a request in the queue and the first one is the one we are serving, we process it
                //This is to preserve the assumed FIFO-ness of the system
                //Why is this needed? Because artificial delays could mess up with FIFO
                if (!fifo.isEmpty() && fifo.get(0).equals(msg.request)) {
                    Pair<Integer, String> best = pendingReads.get(msg.request).stream()
                            .max(Comparator.comparingInt(Pair::getLeft))
                            .orElse(null);
                    if (best != null) {
                        pendingClients.get(msg.request).tell(new SendMsg(best.getRight(), msg.key, best.getLeft()), getSelf());
                        pendingClients.remove(msg.request);
                        fifo.remove(msg.request);
                    }
                }
                //if it is not the above case, then we check if the queue is empty. If it is, it is a reschedule message
                //we can safely drop, as we already processed the request
                else if (fifo.isEmpty()){
                    //too late, scheduled message should be dropped
                }
                //else we schedule a retry a few milliseconds after to see if the state of the system changed
                //and we can process the request
                else {
                    getContext().getSystem().scheduler().scheduleOnce(
                            scala.concurrent.duration.Duration.create(50, "milliseconds"),
                            getSelf(),
                            msg,
                            getContext().getDispatcher(),
                            getSelf()
                    );
                }
            }
        }
        //2. join request: we need a different logic, so we wait to get N items for the key
        //and, when it happens, we send the values and remove
        //all the elements for the key.
        //Note we don't need to preserve FIFO, as nodes join one at a time, thus the property is
        //respected by the constraint itself
        else{
            pendingInternalReads.get(msg.key).add(Pair.of(msg.version, msg.value));
            //System.out.println(pendingInternalReads.get(msg.key).size() + " for " + msg.key);
            if (pendingInternalReads.get(msg.key).size() == N) {
                Pair<Integer, String> best = pendingInternalReads.get(msg.key).stream()
                        .max(Comparator.comparingInt(Pair::getLeft)) // choose highest version
                        .orElse(null);

                if (best != null) {
                    getSelf().tell(new SendMsg(best.getRight(), msg.key, best.getLeft(), true), getSelf());
                    pendingInternalReads.remove(msg.key);
                }
            }
        }
    }


    private void updateValue(UpdateMsg updateMsg){
        clock = clock + 1;
        String request_id = updateMsg.key + "id" + this.id + "c" + clock;

        pendingReads.put(request_id, new ArrayList<>());
        pendingClients.put(request_id, getSender());
        fifo.add(request_id);

        //we create a list of nodes that we need to send the message to
        final ArrayList<ActorRef> nodesForGet = new ArrayList<>();

        //find the nodes to which we need to send the request to
        //here for the moment we assume that the nodes are stored in the hashmap in id order.
        //This may be false, however, hence we need to preserve this when we
        //update the view
        for(int i =0; i<currentView.size(); i++){
            if (id_ref_association.get(currentView.get(i)) >= updateMsg.key && nodesForGet.size() < N){
                nodesForGet.add(currentView.get(i));
            }
        }
        if (nodesForGet.size() != N){
            int temp = nodesForGet.size();
            for(int i=0; i<N-temp; i++){
                nodesForGet.add(currentView.get(i));
            }
        }

        for (int i = 0; i < nodesForGet.size(); i++) {
            nodesForGet.get(i).tell(new Actor.InternalUpdateMsg(updateMsg.key, updateMsg.value, nodesForGet, request_id), getSelf());
        }

        // Schedule a timeout in case not enough responses arrive in time
        getContext().getSystem().scheduler().scheduleOnce(
                scala.concurrent.duration.Duration.create(TIMEOUT_MS, "milliseconds"),
                getSelf(),
                new TimeoutW(updateMsg.key, updateMsg.value, request_id),
                getContext().getDispatcher(),
                ActorRef.noSender()
        );
    }

    // Handles an InternalUpdateMsg by replying with the local value after a random delay.
    private void handleInternalUpdateGet(InternalUpdateMsg msg) {
        if (values.containsKey(msg.key)) {
            Pair<Integer, String> pair = values.get(msg.key);
            int delayMs = 100 + random.nextInt(2901); // Delay between 100ms and 3000ms

            ActorRef originalSender = getSender();

            getContext().getSystem().scheduler().scheduleOnce(
                    scala.concurrent.duration.Duration.create(delayMs, "milliseconds"),
                    () -> originalSender.tell(new Actor.ReceiveUpdMsg(msg.key, pair.getLeft(), pair.getRight(), msg.value_to_update, msg.nodes, msg.request), getSelf()),
                    getContext().getDispatcher()
            );
        }
    }

    // Handles the update process. When enough (>=W) messages have been received, it sends a successful
    // message to the client and then it starts the updating process by updating the version
    // number and then pair it up with the new value and sends it
    private void handleUpdate(ReceiveUpdMsg msg) {
        //we get the received value and stored with the others we have received for the
        //message request
        pendingReads.get(msg.request).add(Pair.of(msg.version, msg.value));

        if (pendingReads.get(msg.request).size() >= W) {
            //if there is a request in the queue and the first one is the one we are serving, we process it
            //This is to preserve the assumed FIFO-ness of the system
            //Why is this needed? Because artificial delays could mess up with FIFO
            if(!fifo.isEmpty() && fifo.get(0).equals(msg.request)){
                Pair<Integer, String> best = pendingReads.get(msg.request).stream()
                        .max(Comparator.comparingInt(Pair::getLeft)) // choose highest version
                        .orElse(null);

                if (best != null) {
                    pendingClients.get(msg.request).tell(new SendMsg("Successful insertion of value " + msg.value_to_update + " into node of key " + String.valueOf(msg.key) + "\n"), getSelf());
                    int versionUpdate = best.getLeft();
                    versionUpdate = versionUpdate + 1;
                    for (int i=0; i<msg.nodes.size(); i++){
                        msg.nodes.get(i).tell(new Actor.NewUpdate(msg.key, versionUpdate, msg.value_to_update), getSelf());
                    }
                    pendingClients.remove(msg.request);
                    fifo.remove(msg.request);
                }
            }
            //if it is not the above case, then we check if the queue is empty. If it is, it is a reschedule message
            //we can safely drop, as we already processed the request
            else if(fifo.isEmpty()){
                //message is unnecessary, should be dropped
            }
            //else we schedule a retry a few milliseconds after to see if the state of the system changed
            //and we can process the request
            else {
            getContext().getSystem().scheduler().scheduleOnce(
                    scala.concurrent.duration.Duration.create(20, "milliseconds"),
                    getSelf(),
                    msg,
                    getContext().getDispatcher(),
                    getSelf()
            );
        }
        }
    }

    private void writeUpdate(NewUpdate msg){
        //we update the value stored by the node
        values.put(msg.key, Pair.of(msg.version, msg.value));
    }

    private void onJoinMessage(JoinMsg msg){
        if(!msg.flag){
            //JOIN OPERATION START
            //after receiving a join message, the new node asks to the node in the message
            //for its view of the system
            msg.actorRef.tell(new RequestView(getSelf()), getSelf());
        }
        else{
            //JOIN OPERATION END
            //we receive a joinMsg from a node that is joining the network. We first update our view and add the
            //ActorRef-id association to the map, which we then sort, then we check for all
            //the values that should be stored in the sender and eliminate those that the node should
            //not keep anymore, according to the replication factor N

            //update view (if it has not already been done) and ActorRef-id association by keeping both of them ordered
            if(!currentView.contains(getSender())){
                boolean insertion = false;
                int index = 0;
                while(!insertion){
                    if (id_ref_association.get(currentView.get(index)) > msg.key){
                        currentView.add(index, getSender());
                        insertion = true;
                    }
                    index = index + 1;
                }
                id_ref_association.put(getSender(), msg.key);
                id_ref_association = Main.sortByValue(id_ref_association);
            }
            //to check if indeed all currentViews are local instances, use the below line of code
            //System.out.println("List identity: " + System.identityHashCode(currentView));
            //get all the items that are contained in the sender and in this node
            ArrayList<Integer> values_to_check = new ArrayList<>();
            //System.out.println(values + " for node " + getSelf());
            for(Integer j: values.keySet()){
                //System.out.println(j + " for node " + getSelf());
                if(j <= msg.key){
                    values_to_check.add(j);
                }
            }
            //System.out.println(values_to_check + " for node " +getSelf());
            //we now need to check whether we should eliminate the item or not
            //we use the following strategy:
            // - we find the set of nodes that should have the value, wrapping around for those greater than the last node, until it is of size N
            // - if the node does not belong to this set, it can safely eliminate its cop of the value, otherwise we have to keep it

            //we iterate for all values we previously found
            //System.out.println(currentView);
            for(int i=0; i<values_to_check.size(); i++){
                //we store the nodes that should have the value here
                ArrayList<ActorRef> nodes_having_value = new ArrayList<>();
                //We find the first node that should have the value and add it plus enough following nodes to satisfy N
                //The modulo is used to add nodes in a clockwise manner (so the last node, for example, would add, with N = 3,
                //itself, the first node and the second one)

                //we first check if the value is less than the first node of the system. If it is, it is trivial to
                //find all the nodes responsible to it, as they are the first N nodes of the system
                if(values_to_check.get(i) <= id_ref_association.get((currentView.get(0)))){
                    for(int k=0; k<N; k++){
                        nodes_having_value.add(currentView.get((k)%currentView.size()));
                        //System.out.println("Added the following node " + currentView.get((k)%currentView.size()) + " for actor " + getSelf());
                    }
                }
                if(nodes_having_value.size() != N){
                    for(int j=0; j<currentView.size(); j++){
                        if(values_to_check.get(i) > id_ref_association.get((currentView.get(j))) && values_to_check.get(i) <= id_ref_association.get(currentView.get((j+1)%currentView.size()))){
                            int temp = j+1;
                            for(int k=0; k<N; k++){
                                nodes_having_value.add(currentView.get((temp)%currentView.size()));
                                temp = temp + 1;
                                //System.out.println("Added the following node " + currentView.get((temp)%currentView.size()) + " for actor " + getSelf());
                            }
                            break;
                        }
                    }
                }

                //if we have not found enough nodes, this means that the value was greater than N, thus it
                //should have been stored in the first N nodes of the system
                //For precaution, we check if nodes_having_value does at least have some values (in reality, if it check the conditions
                //it should never even have a value in it, but better safe than sorry)
                //System.out.println(nodes_having_value + " for node " + getSelf() + " and value " + values_to_check.get(i));
                if(nodes_having_value.size() != N){
                    int size_before_insertion = nodes_having_value.size();
                    for(int j=0; j<N-size_before_insertion; j++){
                        nodes_having_value.add(currentView.get(j));
                    }
                }
                //we check if the current node is present in the set we created: if not, we can delete the value
                //System.out.println(nodes_having_value + " for node " + getSelf());
                if(!nodes_having_value.contains(getSelf())){
                    values.remove(values_to_check.get(i));
                }
            }
            System.out.println("Values in node " + id_ref_association.get(getSelf()) + ": " + values);
        }
    }

    private void onRequestView(RequestView msg){
        //It replies with the current view of the node
        msg.actorRef.tell(new ImplementView(this.currentView, this.id_ref_association, this.clients), getSelf());       
    }

    private void onImplementView(ImplementView msg){
        //We set the current view and the ActorRef-id association
        this.currentView = msg.nodes;
        this.id_ref_association = msg.map;
        this.clients.addAll(msg.clients);
        //we search the right neighbour of the node, as requested
        //note that we never implement an exception for the case where nodes_to_contact
        //remains empty even after the search, as it is assumable from the project description
        //that such a case never happens/should never happen
        ArrayList<ActorRef> nodes_to_contact = new ArrayList<>();
        int temp = 0;
        while(nodes_to_contact.size() != 1 && temp < currentView.size()){
            if (id_ref_association.get(currentView.get(temp)) > this.id){
                nodes_to_contact.add(currentView.get(temp));
            }
            temp = temp + 1;
        }
        //we contact the node we obtained previously
        nodes_to_contact.get(0).tell(new RequestValues(this.id), getSelf());
    }

    private void provideValues(RequestValues msg){
        //we find all the values that should be stored in the new node
        Map<Integer, Pair<Integer,String>> available_values = new HashMap<>();
        for(Integer i: values.keySet()){
            if(i <= msg.id){
                available_values.put(i, values.get(i));
            }
        }
        getSender().tell(new SendValues(available_values), getSelf());
    }

    private void setValuesAndRead(SendValues msg){
        //we set the value obtained by the neighbour node
        this.values = msg.values;
        for(Integer j: values.keySet()){
            //we add each element to the pendingInternalReads - this is done so to prevent the join
            //process to end when it shouldn't
            pendingInternalReads.put(j, new ArrayList<>());
        }
        //for each value contained there, we perform a read to get the most up to date value
        for(Integer j: values.keySet()){
            //same process as the normal read, we just don't declare a timeout
            ArrayList<ActorRef> nodesForGet = new ArrayList<>();
            for(int i =0; i<currentView.size(); i++){
                //System.out.println(currentView.get(i));
                if (id_ref_association.get(currentView.get(i)) >= j && nodesForGet.size() < N){
                    nodesForGet.add(currentView.get(i));
                    //System.out.println("added node " + currentView.get(i));
                }
            }
            if (nodesForGet.size() != N){
                int temp = nodesForGet.size();
                for(int i=0; i<N-temp; i++){
                    nodesForGet.add(currentView.get(i));
                }
            }

            for (int i = 0; i < N; i++) {
                //System.out.println(nodesForGet.get(i));
                nodesForGet.get(i).tell(new Actor.InternalGetMsg(j, true), getSelf());
                //System.out.println("Message sent to " + nodesForGet.get(i));
            }

        }
    }

    private void internalSetValue(SendMsg msg){
        //we check the version of the retrieved object. If it is higher than the stored
        //one, it means it's newer and we need to substitute the old value with the new one.
        if(values.get(msg.key).getLeft() <= msg.version){
            values.put(msg.key, Pair.of(msg.version, msg.value));
        }
        //this means that the request was sent in the scope of a join operation.
        //Hence, we need to check if we have updated all the values by checking whether
        //pendingReads is empty. If it is, then we can announce our presence to the network.
        //If it isn't, then it means we still need to update some values, so we do nothing.
        if(msg.flag){
            if(pendingInternalReads.isEmpty()){
                for(int i =0; i<currentView.size(); i++){
                    currentView.get(i).tell(new JoinMsg(this.id, true), getSelf());
                }
                //Now we add the node itself to its local curretnView.
                //We, however, keep the view ordered for simplicity's sake
                boolean insertion = false;
                int index = 0;
                while(!insertion){
                    if (id_ref_association.get(currentView.get(index)) > this.id){
                        currentView.add(index, this.self());
                        insertion = true;
                    }
                    index = index + 1;
                }
                id_ref_association.put(this.self(), this.id);
                id_ref_association = Main.sortByValue(id_ref_association);
                //System.out.println(currentView);
                System.out.println("Values in node " + id_ref_association.get(getSelf()) + ": " + values);

                //we communicate to the clients that we are joining the system
                for (ActorRef client : clients) {
                    client.tell(new UpdateClientView(getSelf(),false), getSelf());
                }

            }

        }
    }

    // Called when timeout occurs for a pending read. If quorum was not reached, responds to the client with null.
    private void onTimeoutRead(Timeout timeout) {
        if (pendingReads.get(timeout.request).size() < R) {
            pendingClients.get(timeout.request).tell(new SendMsg("Read of value failed"), getSelf());
            pendingReads.get(timeout.request).clear();
            fifo.remove(timeout.request);
        }
    }

    // Called when timeout occurs for a pending write. If quorum was not reached, responds to the client with null.
    private void onTimeoutWrite(TimeoutW timeout) {
        if (pendingReads.get(timeout.request).size() < R) {
            pendingClients.get(timeout.request).tell(new SendMsg("Write of value " + timeout.value + " failed"), getSelf());
            pendingReads.get(timeout.request).clear();
            fifo.remove(timeout.request);
        }
    }

    
    // Updates the view of known nodes (excluding itself)
    private void updateView(UpdateView msg) {
        this.currentView.addAll(msg.nodes);
    }

    // Sets the local key-value store with initial values.
    private void setValues(SetValues msg) {
        this.values.putAll(msg.values);
    }

    private void setIdAssociation(SetIdAssociation msg){
        this.id_ref_association.putAll(msg.map);
    }

    private void onLeaveMsg(LeaveMsg msg) {
        if (msg.leavingNode.equals(getSelf())){
            // Notify all other nodes
            System.out.println("Initiating leave for node " + this.id);
            for (ActorRef node : currentView) {
                if (!node.equals(getSelf())) {
                    node.tell(new LeaveMsg(this.id, getSelf()), getSelf());
                }
            }

            // For each value, find new responsible nodes and update data
            currentView.remove(getSelf());
            for (Map.Entry<Integer, Pair<Integer, String>> entry : values.entrySet()) {
                List<ActorRef> newResponsibleNodes = findResponsibleNodes(entry.getKey(), currentView);
                for (ActorRef node : newResponsibleNodes) {
                    node.tell(new TransferDataMsg(entry.getKey(), entry.getValue()), getSelf());
                }
            }

            // Contact clients
            for (ActorRef client : clients) {
                client.tell(new UpdateClientView(getSelf(),true), getSelf());
            }
        }
        else {
            // Remove the leaving node from the view and id_ref_association
            currentView.remove(msg.leavingNode);
            id_ref_association.remove(msg.leavingNode);
        }
    }

    // Helper method to find responsible nodes
    private List<ActorRef> findResponsibleNodes(int key, List<ActorRef> view) {
        List<ActorRef> responsible = new ArrayList<>();
        // Add the nodes that are responsible for the key to the list
        for (int i = 0; i < view.size(); i++) {
            if (id_ref_association.get(view.get(i)) >= key && responsible.size() < N) {
                responsible.add(view.get(i));
            }
        }
        // If the list is not of size N because the key is greater than the last node, add the first N - responsible.size() nodes to the list
        if (responsible.size() != N) {
            int temp = responsible.size();
            for (int i = 0; i < N - temp; i++) {
                responsible.add(view.get(i));
            }
        }
        return responsible;
    }

    private void onTransferDataMsg(TransferDataMsg msg) {
        // Add the data to values
        values.put(msg.key, msg.value);
        //System.out.println("Value for " + getSelf() + ":" + values);
    }

    private void SetClientsView(SetClientsView msg) {
        // Set the client view
        this.clients.addAll(msg.clients);
    }

    private void printValues(PrintValues msg) {
        System.out.println("Values in node " + this.id + ":");
        for (Map.Entry<Integer, Pair<Integer, String>> entry : values.entrySet()) {
            System.out.println("Key: " + entry.getKey() + " -> Version: " + entry.getValue().getLeft() + ", Value: " + entry.getValue().getRight());
        }
    }

    private void onRecoveryMsg(RecoveryMsg msg) {
        System.out.println("Node " + this.id + " recovered");
        getContext().become(active());
        msg.helperNode.tell(new JoinMsg(this.id, true), getSelf());
    }

    // ---- Message classes below ----

    public static class Timeout implements Serializable {
        public final int key;
        public String request;
        public Timeout(int key, String request) {
            this.key = key;
            this.request = request;
        }

    }

    public static class TimeoutW implements Serializable {
        public final int key;
        public final String value;
        public String request;
        public TimeoutW(int key, String value, String request) {
            this.key = key; this.value = value; this.request = request;
        }
    }

    public static class UpdateView implements Serializable {
        public final ArrayList<ActorRef> nodes;
        public UpdateView(ArrayList<ActorRef> nodes) {
            this.nodes = nodes;
        }
    }

    public static class ImplementView implements Serializable {
        public final ArrayList<ActorRef> nodes;
        public final Map<ActorRef, Integer> map;
        public ArrayList<ActorRef> clients;
        public ImplementView(ArrayList<ActorRef> nodes, Map<ActorRef, Integer> map, ArrayList<ActorRef> clients) {
            this.nodes = nodes;
            this.map = map;
            this.clients = clients;
        }
    }

    public static class RequestView implements Serializable{
        public ActorRef actorRef;
        public RequestView(ActorRef actorRef){
            this.actorRef = actorRef;
        }
    }

    public static class RequestValues implements Serializable{
        public int id;
        public RequestValues(int id){
            this.id = id;
        }
    }

    public static class SendValues implements Serializable{
        public Map<Integer, Pair<Integer,String>> values;

        public SendValues(Map<Integer, Pair<Integer, String>> values) {
            this.values = values;
        }
    }

    public static class SetValues implements Serializable {
        public final Map<Integer,Pair<Integer,String>> values;
        public SetValues(Map<Integer,Pair<Integer,String>> values) {
            this.values = values;
        }
    }

    public static class SetIdAssociation implements Serializable {
        public final Map<ActorRef, Integer> map;
        public SetIdAssociation(Map<ActorRef, Integer> map) {
            this.map = map;
        }
    }

    public static class SendMsg implements Serializable {
        public String value;
        public int key;
        public int version;
        public boolean flag = false; //used for the joining process
        //constructor used for the get, to reuse functions also for the internal one
        public SendMsg(String value, int key, int version) {
            this.value = value;
            this.key = key;
            this.version = version;
        }
        //constructor used for the join process, to send back values
        public SendMsg(String value, int key, int version, boolean flag) {
            this.value = value;
            this.key = key;
            this.version = version;
            this.flag = true;
        }
        //constructor used for error messages and for the feedback of the update
        public SendMsg(String value) {
            this.value = value;
        }
    }

    public static class InternalGetMsg implements Serializable {
        public final int key;
        public boolean flag; //used to manage internal reads for join
        public String request;
        public InternalGetMsg(int key, String request) {
            this.key = key;
            this.flag = false;
            this.request = request;
        }
        public InternalGetMsg(int key, boolean flag) {
            this.key = key;
            this.flag = true;
        }
    }

    public static class InternalUpdateMsg implements Serializable {
        public final int key;
        public final String value_to_update;
        public final ArrayList<ActorRef> nodes;
        public String request;
        public InternalUpdateMsg(int key, String value_to_update, ArrayList<ActorRef> nodes, String request) {
            this.key = key;
            this.value_to_update = value_to_update;
            this.nodes = nodes;
            this.request = request;
        }
    }

    public static class ReceiveMsg implements Serializable {
        public int key;
        public int version;
        public String value;
        public boolean flag; //used for the internal read of the join operation
        public String request;
        public ReceiveMsg(int key, int version, String value, String request) {
            this.key = key;
            this.version = version;
            this.value = value;
            this.flag = false;
            this.request = request;
        }
        public ReceiveMsg(int key, int version, String value, boolean flag) {
            this.key = key;
            this.version = version;
            this.value = value;
            this.flag = true;
        }
    }

    public static class JoinMsg implements Serializable{
        public int key;
        public ActorRef actorRef;
        public boolean flag = false;
        public JoinMsg(int key, ActorRef actorRef){
            this.key = key;
            this.actorRef = actorRef;
        }
        public JoinMsg(int key, boolean flag){
            this.key = key;
            this.flag = true;
        }
    }

    public static class ReceiveUpdMsg implements Serializable {
        public int key;
        public int version;
        public String value;
        public String value_to_update;
        public ArrayList<ActorRef> nodes;
        public String request;
        public ReceiveUpdMsg(int key, int version, String value, String value_to_update, ArrayList<ActorRef> nodes, String request) {
            this.key = key;
            this.version = version;
            this.value = value;
            this.value_to_update = value_to_update;
            this.nodes = nodes;
            this.request = request;
        }
    }

    public static class NewUpdate implements Serializable{
        public int key;
        public int version;
        public String value;

        public NewUpdate(int key, int version, String value) {
            this.key = key;
            this.version = version;
            this.value = value;
        }
    }

    public static class LeaveMsg implements Serializable {
        public final int id;
        public final ActorRef leavingNode;
        public LeaveMsg(int id, ActorRef leavingNode) {
            this.id = id;
            this.leavingNode = leavingNode;
        }
    }

    public static class TransferDataMsg implements Serializable {
        public final int key;
        public final Pair<Integer, String> value;
        public TransferDataMsg(int key, Pair<Integer, String> value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class SetClientsView implements Serializable {
        public final ArrayList<ActorRef> clients;
        public SetClientsView(ArrayList<ActorRef> clients) {
            this.clients = clients;
        }
    }

    public static class CrashMsg implements Serializable {
        public final boolean isCrashed;
        public CrashMsg(boolean isCrashed) {
            this.isCrashed = isCrashed;
        }
    }

    public static class RecoveryMsg implements Serializable {
        public final ActorRef helperNode;
        public RecoveryMsg(ActorRef helperNode) {
            this.helperNode = helperNode;
        }
    }

    public static class PrintValues implements Serializable {}

    @Override
    public Receive createReceive() {
        return active();
    }

    private Receive active() {
        return receiveBuilder()
            .match(GetMsg.class, this::getValue)
            .match(UpdateMsg.class, this::updateValue)
            .match(UpdateView.class, this::updateView)
            .match(SetValues.class, this::setValues)
            .match(InternalGetMsg.class, this::handleInternalGet)
            .match(ReceiveMsg.class, this::receiveResponses)
            .match(Timeout.class, this::onTimeoutRead)
            .match(TimeoutW.class, this::onTimeoutWrite)
            .match(InternalUpdateMsg.class, this::handleInternalUpdateGet)
            .match(ReceiveUpdMsg.class, this::handleUpdate)
            .match(SetIdAssociation.class, this::setIdAssociation)
            .match(NewUpdate.class, this::writeUpdate)
            .match(JoinMsg.class, this::onJoinMessage)
            .match(RequestView.class, this::onRequestView)
            .match(ImplementView.class, this::onImplementView)
            .match(RequestValues.class, this::provideValues)
            .match(SendValues.class, this::setValuesAndRead)
            .match(SendMsg.class, this::internalSetValue)
            .match(LeaveMsg.class, this::onLeaveMsg)
            .match(TransferDataMsg.class, this::onTransferDataMsg)
            .match(SetClientsView.class, this::SetClientsView)
            .match(CrashMsg.class, msg -> {
                getContext().become(crashed());
            })
            .match(PrintValues.class, this::printValues)
            .build();
    }

    private Receive crashed() {
        return receiveBuilder()
            .match(RecoveryMsg.class, this::onRecoveryMsg)
            .match(PrintValues.class, this::printValues)
            .matchAny(msg -> {
                System.out.println("Node " + this.id + " is crashed. Ignoring: " + msg.getClass().getSimpleName());
            })
            .build();
    }
}
