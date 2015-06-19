/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.tomitribe.jcache.examples;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.tomitribe.jcache.examples.application.BookService;
import com.tomitribe.jcache.examples.entities.Book;
import com.tomitribe.jcache.examples.interceptor.CacheInterceptor;
import com.tomitribe.jcache.examples.producers.CacheProducer;
import com.tomitribe.jcache.examples.producers.ObjectCacheProducer;
import com.tomitribe.jcache.examples.service.InterceptedService;
import com.tomitribe.jcache.examples.service.ServiceApplication;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.jee.jpa.unit.PersistenceUnit;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.EnableServices;
import org.apache.openejb.testing.Module;
import org.apache.openejb.util.NetworkUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

@RunWith(ApplicationComposer.class)
@EnableServices(value = {"jax-rs"}, httpDebug = true)
public class AppComposer {

    public static final int port = NetworkUtil.getNextAvailablePort();

    @Module
    @Classes(cdi = true, value = {
            ServiceApplication.class,
            BookService.class,
            Book.class,
            CacheInterceptor.class,
            ObjectCacheProducer.class,
            CacheProducer.class,
            InterceptedService.class})
    public WebApp app() {
        return new WebApp().contextRoot("test");
    }

    @Configuration
    public Properties config() throws Exception {
        final Properties p = new Properties();
        p.put("bookDatabase", "new://Resource?type=DataSource");
        p.put("bookDatabase.JdbcDriver", "org.hsqldb.jdbcDriver");
        p.put("bookDatabase.JdbcUrl", "jdbc:hsqldb:mem:bookDB");

        p.put("httpejbd.port", Integer.toString(port));

        p.put("openejb.jaxrs.providers.auto", "false");

        //One or the other?
        p.put("openejb.cxf.jax-rs.providers", "com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider");
        p.put("cxf.jaxrs.providers", "com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider");

        return p;
    }

    @Module
    public PersistenceUnit persistence() {
        final PersistenceUnit unit = new PersistenceUnit("book-pu");
        unit.setJtaDataSource("bookDatabase");
        unit.setNonJtaDataSource("bookDatabaseUnmanaged");
        unit.getClazz().add(Book.class.getName());
        unit.setProperty("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
        return unit;
    }

    @Inject
    private BookService bookService;

    @Test
    public void getBook() throws Exception {

        Book book = new Book();
        book.setBookTitle("War and Peace");

        final int id = bookService.addBook(book);

        //First call caches stored
        book = getClient().path("api/intercept/" + id).get(Book.class);

        assertEquals("Invalid book id", id, book.getBookId());
        assertEquals("Invalid book name", "War and Peace", book.getBookTitle());

        bookService.update(id, "Harry met Sally");

        //Get cached version
        book = getClient().path("api/intercept/" + id).get(Book.class);

        assertEquals("Invalid book id", id, book.getBookId());
        assertEquals("Invalid book name", "War and Peace", book.getBookTitle());

        //Get stored version
        bookService.clear(id);
        book = getClient().path("api/intercept/" + id).get(Book.class);

        assertEquals("Invalid book id", id, book.getBookId());
        assertEquals("Invalid book name", "Harry met Sally", book.getBookTitle());

    }

    private WebClient getClient() throws URISyntaxException {
        final List<JacksonJaxbJsonProvider> pl = Arrays.asList(new JacksonJaxbJsonProvider());
        return WebClient.create("http://localhost:" + port + "/test", pl).type(MediaType.APPLICATION_JSON_TYPE).accept(MediaType.APPLICATION_JSON_TYPE);
    }
}
