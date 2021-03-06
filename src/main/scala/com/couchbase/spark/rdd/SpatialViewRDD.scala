/**
 * Copyright (C) 2015 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package com.couchbase.spark.rdd

import java.util.concurrent.TimeUnit

import com.couchbase.client.core.BackpressureException
import com.couchbase.client.core.time.Delay
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.error.{CouchbaseOutOfMemoryException, TemporaryFailureException}
import com.couchbase.client.java.util.retry.RetryBuilder
import com.couchbase.client.java.view.SpatialViewQuery
import com.couchbase.spark.connection.{CouchbaseConnection, CouchbaseConfig}
import com.couchbase.spark.internal.LazyIterator
import org.apache.spark.{Partition, TaskContext, SparkContext}
import org.apache.spark.rdd.RDD

import rx.lang.scala.JavaConversions._

case class CouchbaseSpatialViewRow(id: String, key: Any, value: Any, geometry: JsonObject)

class SpatialViewRDD
  (@transient sc: SparkContext, viewQuery: SpatialViewQuery, bucketName: String = null)
  extends RDD[CouchbaseSpatialViewRow](sc, Nil) {

  private val cbConfig = CouchbaseConfig(sc.getConf)

  override def compute(split: Partition, context: TaskContext):
    Iterator[CouchbaseSpatialViewRow] = {
    val bucket = CouchbaseConnection().bucket(cbConfig, bucketName).async()

    val maxDelay = cbConfig.retryOpts.maxDelay
    val minDelay = cbConfig.retryOpts.minDelay
    val maxRetries = cbConfig.retryOpts.maxTries

    LazyIterator {
      toScalaObservable(bucket.query(viewQuery).retryWhen(
        RetryBuilder
          .anyOf(classOf[BackpressureException])
          .delay(Delay.exponential(TimeUnit.MILLISECONDS, maxDelay, minDelay))
          .max(maxRetries)
          .build()
        ))
        .flatMap(result => toScalaObservable(result.rows()))
        .map(row => CouchbaseSpatialViewRow(row.id(), row.key(), row.value(), row.geometry()))
        .toBlocking
        .toIterable
        .iterator
    }
  }

  override protected def getPartitions: Array[Partition] = Array(new CouchbasePartition(0))

}

object SpatialViewRDD {
  def apply(sc: SparkContext, bucketName: String, viewQuery: SpatialViewQuery) =
    new SpatialViewRDD(sc, viewQuery, bucketName)
}
