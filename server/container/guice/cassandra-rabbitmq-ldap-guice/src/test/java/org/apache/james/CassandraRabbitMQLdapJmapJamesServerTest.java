/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james;

import static org.apache.james.jmap.draft.JmapJamesServerContract.JAMES_SERVER_HOST;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.SwiftBlobStoreExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRabbitMQLdapJmapJamesServerTest {
    interface UserFromLdapShouldLogin {

        @Test
        default void userFromLdapShouldLoginViaImapProtocol(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER.asString(), PASSWORD)).isTrue();
        }
    }

    interface ContractSuite extends JmapJamesServerContract, UserFromLdapShouldLogin, JamesServerContract {}

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithSwift implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseJamesServerExtensionBuilder(BlobStoreConfiguration.builder()
                    .objectStorage()
                    .disableCache()
                    .deduplication())
            .extension(new SwiftBlobStoreExtension())
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithAwsS3 implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseJamesServerExtensionBuilder(BlobStoreConfiguration.builder()
                    .objectStorage()
                    .disableCache()
                    .deduplication())
            .extension(new AwsS3BlobStoreExtension())
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithoutSwiftOrAwsS3 implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseJamesServerExtensionBuilder(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .passthrough())
            .build();
    }

    JamesServerBuilder baseJamesServerExtensionBuilder(BlobStoreConfiguration blobStoreConfiguration) {
        return new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(blobStoreConfiguration)
                .searchConfiguration(SearchConfiguration.elasticSearch())
                .build())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapTestExtension())
            .server(configuration -> CassandraRabbitMQLdapJamesServerMain.createServer(configuration)
                .overrideWith(new TestJMAPServerModule())
                .overrideWith(JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE));
    }
}
