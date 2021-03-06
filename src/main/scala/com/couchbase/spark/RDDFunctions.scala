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
package com.couchbase.spark

import com.couchbase.client.java.view.ViewQuery
import com.couchbase.spark.internal.{OnceIterable, LazyIterator}
import com.couchbase.spark.rdd.{ViewRDD, CouchbaseViewRow, KeyValueRDD}
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Observable

import scala.reflect.ClassTag

import com.couchbase.client.java.document.Document
import com.couchbase.spark.connection.{KeyValueAccessor, CouchbaseConnection, CouchbaseConfig}

import org.apache.spark.rdd.RDD

class RDDFunctions[T](rdd: RDD[T]) extends Serializable {

  private val cbConfig = CouchbaseConfig(rdd.sparkContext.getConf)

  /**
   * Convert a RDD[String] to a RDD[D]. It's available if T is String.
   *
   * @param ct
   * @param evidence
   * @tparam D
   * @return
   */
  def couchbaseGet[D <: Document[_]](bucketName: String = null)
    (implicit ct: ClassTag[D], evidence: RDD[T] <:< RDD[String]): RDD[D] = {

    val idRDD: RDD[String] = rdd
    idRDD.mapPartitions { valueIterator =>
      if (valueIterator.isEmpty) {
        Iterator[D]()
      } else {
        new KeyValueAccessor[D](cbConfig, OnceIterable(valueIterator).toSeq, bucketName).compute()
      }
    }
  }

}
