/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.stereotype.Component

@Component
class ShrinkClusterTask extends AbstractClusterWideClouddriverTask {
  @Canonical
  static class ShrinkConfig {
    boolean allowDeleteActive = false
    int shrinkToSize = 1
    boolean retainLargerOverNewer = false
  }

  @Override
  String getClouddriverOperation() {
    "destroyServerGroup"
  }

  List<Map> filterActiveGroups(boolean includeActive, List<Map> serverGroups) {
    if (includeActive) {
      return serverGroups
    }
    return serverGroups.findAll { !isActive(it) }
  }

  @Override
  List<Map> filterServerGroups(Stage stage, String account, String region, List<Map> serverGroups) {
    List<Map> filteredGroups = super.filterServerGroups(stage, account, region, serverGroups)
    if (!filteredGroups) {
      return []
    }

    def config = stage.mapTo(ShrinkConfig)
    filteredGroups = filterActiveGroups(config.allowDeleteActive, filteredGroups)
    def comparators = []
    int dropCount = config.shrinkToSize - (serverGroups.size() - filteredGroups.size())
    if (config.allowDeleteActive) {
      comparators << new IsActive()
    }
    if (config.retainLargerOverNewer) {
      comparators << new InstanceCount()
    }
    comparators << new CreatedTime()

    //result will be sorted in priority order to retain
    def prioritized = filteredGroups.sort(false, new CompositeComparitor(comparators))

    return prioritized.drop(dropCount)
  }
}
