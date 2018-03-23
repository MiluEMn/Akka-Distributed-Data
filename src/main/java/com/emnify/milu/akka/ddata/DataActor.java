package com.emnify.milu.akka.ddata;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ddata.DistributedData;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.ORSetKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.Replicator.Changed;
import akka.cluster.ddata.Replicator.Subscribe;
import akka.cluster.ddata.Replicator.Unsubscribe;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateResponse;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DataActor extends AbstractActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().system(), getSelf());
  private final ActorRef replicator = DistributedData.get(getContext().system()).replicator();
  private final Cluster cluster = Cluster.get(getContext().system());
  private final Key<ORSet<Integer>> dataKey = ORSetKey.create("key");

  private final Cancellable tickTask = getContext().system().scheduler().schedule(
      Duration.Zero(),
      Duration.create(5, TimeUnit.SECONDS),
      this::randomUpdate,
      getContext().system().dispatcher()
  );

  public static Props props() {

    return Props.create(DataActor.class);
  }

  @Override
  public void preStart() {

    Subscribe<ORSet<Integer>> subscribe = new Subscribe<>(dataKey, getSelf());
    replicator.tell(subscribe, ActorRef.noSender());
  }

  @Override
  public void postStop() {

    tickTask.cancel();
    Unsubscribe<ORSet<Integer>> unsubscribe = new Unsubscribe<>(dataKey, getSelf());
    replicator.tell(unsubscribe, ActorRef.noSender());
  }

  @Override
  public Receive createReceive() {

    return receiveBuilder()
        .match(UpdateResponse.class, resp -> log.info("Got UpdateResponse: {}", resp))
        .match(Changed.class, this::receiveChange)
        .matchAny(this::logReceive)
        .build();
  }

  private void receiveChange(Changed<ORSet<Integer>> changed) {

    String elements = changed.dataValue().getElements().stream()
        .map(elem -> elem.toString())
        .reduce((elem1, elem2) -> elem1 + ", " + elem2)
        .orElse("");

    log.info("DataSet changed, current elements: [ {} ]", elements);
  }

  private void logReceive(Object msg) {

    log.info("Got: {}", msg);
  }

  private void randomUpdate() {

    int data = ThreadLocalRandom.current().nextInt(42, 64);
    Update<ORSet<Integer>> update;
    if(ThreadLocalRandom.current().nextBoolean()) {
      update = new Update<>(
          dataKey,
          ORSet.create(),
          Replicator.writeLocal(),
          current -> current.add(cluster, data)
      );
    } else {
      update = new Update<>(
          dataKey,
          ORSet.create(),
          Replicator.writeLocal(),
          current -> current.remove(cluster, data)
      );
    }
    replicator.tell(update, getSelf());
  }
}
