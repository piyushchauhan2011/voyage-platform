package com.voyage.mastery;

import com.voyage.mastery.collections.CollectionsDemo;
import com.voyage.mastery.concurrency.ConcurrencyDemo;
import com.voyage.mastery.concurrency.DoubleBookingDemo;
import com.voyage.mastery.jvm.JvmDemo;
import com.voyage.mastery.oop.OopDemo;
import com.voyage.mastery.streams.StreamsDemo;

public class Main {

  public static void main(String[] args) {
    OopDemo.run();
    CollectionsDemo.run();
    StreamsDemo.run();
    JvmDemo.run();
    ConcurrencyDemo.run();
    DoubleBookingDemo.run();
  }
}
