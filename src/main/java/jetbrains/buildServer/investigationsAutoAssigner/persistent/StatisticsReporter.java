/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.investigationsAutoAssigner.persistent;

import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;

public class StatisticsReporter {
  private final StatisticsDao myStatisticsDao;
  private Statistics myStatistics;

  StatisticsReporter(StatisticsDao statisticsDao) {
    myStatistics = statisticsDao.read();
    myStatisticsDao = statisticsDao;
  }

  public void reportShownButton() {
    myStatistics.shownButtonsCount++;
  }

  public void reportClickedButton() {
    myStatistics.clickedButtonsCount++;
  }

  public void reportAssignedInvestigations(int count) {
    myStatistics.assignedInvestigationsCount += count;
  }

  public void reportWrongInvestigation(int count) {
    myStatistics.wrongInvestigationsCount += count;
  }

  public void saveDataOnDisk() {
    if (StringUtil.isTrue(TeamCityProperties.getProperty(Constants.STATISTICS_ENABLED, "false"))) {
      myStatisticsDao.write(myStatistics);
    }
  }
}