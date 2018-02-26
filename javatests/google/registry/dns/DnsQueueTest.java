// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.dns;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.JUnitBackports.assertThrows;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;

import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DnsQueue}. */
@RunWith(JUnit4.class)
public class DnsQueueTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();
  private DnsQueue dnsQueue;
  private final FakeClock clock = new FakeClock(DateTime.parse("2010-01-01T10:00:00Z"));

  @Before
  public void init() {
    dnsQueue = DnsQueue.createForTesting(clock);
    dnsQueue.leaseTasksBatchSize = 10;
  }

  @Test
  public void test_addHostRefreshTask_success() throws Exception {
    createTld("tld");
    dnsQueue.addHostRefreshTask("octopus.tld");
    assertTasksEnqueued(
        "dns-pull",
        new TaskMatcher()
            .param("Target-Type", "HOST")
            .param("Target-Name", "octopus.tld")
            .param("Create-Time", "2010-01-01T10:00:00.000Z")
            .param("tld", "tld"));
  }

  @Test
  public void test_addHostRefreshTask_failsOnUnknownTld() throws Exception {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              try {
                dnsQueue.addHostRefreshTask("octopus.notatld");
              } finally {
                assertNoTasksEnqueued("dns-pull");
              }
            });
    assertThat(thrown)
        .hasMessageThat()
        .contains("octopus.notatld is not a subordinate host to a known tld");
  }

  @Test
  public void test_addDomainRefreshTask_success() throws Exception {
    createTld("tld");
    dnsQueue.addDomainRefreshTask("octopus.tld");
    assertTasksEnqueued(
        "dns-pull",
        new TaskMatcher()
            .param("Target-Type", "DOMAIN")
            .param("Target-Name", "octopus.tld")
            .param("Create-Time", "2010-01-01T10:00:00.000Z")
            .param("tld", "tld"));
  }

  @Test
  public void test_addDomainRefreshTask_failsOnUnknownTld() throws Exception {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              try {
                dnsQueue.addDomainRefreshTask("fake.notatld");
              } finally {
                assertNoTasksEnqueued("dns-pull");
              }
            });
    assertThat(thrown).hasMessageThat().contains("TLD notatld does not exist");
  }
}
