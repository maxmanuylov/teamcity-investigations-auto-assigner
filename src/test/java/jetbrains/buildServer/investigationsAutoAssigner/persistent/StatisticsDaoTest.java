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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import jetbrains.buildServer.investigationsAutoAssigner.common.Constants;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StatisticsDaoTest {
  private StatisticsDao myStatisticsDao;
  private Path myPluginsDataDir;

  @BeforeMethod
  public void setUp() throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    myPluginsDataDir = fs.getPath("/some_path");
    Files.createDirectory(myPluginsDataDir);
    myStatisticsDao = new StatisticsDao(myPluginsDataDir);
  }

  @Test
  public void testReadCorrectStatistics() throws IOException {
    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Files.createDirectory(assignerDataDir);

    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);
    Files.write(myStatisticsPath, readGold("StatisticsDaoTest_TestReadCorrectStatistics_Gold.txt").getBytes());

    Statistics statistics = myStatisticsDao.read();

    Assert.assertEquals(statistics.getVersion(), Constants.STATISTICS_FILE_VERSION);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.shownButtonsCount), 13);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.clickedButtonsCount), 7);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.assignedInvestigationsCount), 3);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.wrongInvestigationsCount), 2);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.buildWithSuggestionsCount), 2);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.savedSuggestionsCount), 3);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.processedBuildsCount), 3);
    Assert.assertEquals(statistics.get(StatisticsValuesEnum.changesInBuildsCount), 10);
  }

  @Test
  public void testReadIncorrect() throws IOException {
    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Files.createDirectory(assignerDataDir);

    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);
    Files.write(myStatisticsPath, readGold("StatisticsDaoTest_TestReadIncorrect_Gold.txt").getBytes());

    Statistics statistics = myStatisticsDao.read();

    Assert.assertEquals(new Statistics(), statistics);
  }

  @Test
  public void testReadOldVersion() throws IOException {
    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Files.createDirectory(assignerDataDir);

    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);
    Files.write(myStatisticsPath, readGold("StatisticsDaoTest_TestReadOldVersion_Gold.txt").getBytes());

    Statistics statistics = myStatisticsDao.read();

    Assert.assertEquals(new Statistics(), statistics);
  }

  @Test
  public void testReadNotExistDirectory() {
    Statistics statistics = myStatisticsDao.read();

    Assert.assertEquals(new Statistics(), statistics);
  }

  @Test
  public void testReadFileNotExist() throws IOException {
    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Files.createDirectory(assignerDataDir);

    Statistics statistics = myStatisticsDao.read();

    Assert.assertEquals(new Statistics(), statistics);
  }

  private String readGold(String resourceName) {
    File resource = new File(SuggestionsDao.class.getResource("/gold/" + resourceName).getFile());

    try {
      return new String(Files.readAllBytes(resource.toPath()));
    } catch (IOException e) {
      throw new RuntimeException("Can not read GOLD " + resourceName);
    }
  }

  @Test
  public void testWriteUpdatedStatistics() throws IOException {
    Assert.assertEquals(myStatisticsDao.read(), new Statistics());
    Statistics statistics = new Statistics();

    statistics.increase(StatisticsValuesEnum.shownButtonsCount, 1);
    statistics.increase(StatisticsValuesEnum.clickedButtonsCount, 2);
    statistics.increase(StatisticsValuesEnum.assignedInvestigationsCount, 3);
    statistics.increase(StatisticsValuesEnum.wrongInvestigationsCount, 4);
    statistics.increase(StatisticsValuesEnum.buildWithSuggestionsCount, 2);
    statistics.increase(StatisticsValuesEnum.savedSuggestionsCount, 3);
    statistics.increase(StatisticsValuesEnum.processedBuildsCount, 3);
    statistics.increase(StatisticsValuesEnum.changesInBuildsCount, 10);
    myStatisticsDao.write(statistics);

    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);
    String fileContent = new String(Files.readAllBytes(myStatisticsPath));

    Assert.assertTrue(fileContent.contains("\"version\":\"1.4\""));
    Assert.assertTrue(fileContent.contains("\"clickedButtonsCount\":2"));
    Assert.assertTrue(fileContent.contains("\"savedSuggestionsCount\":3"));
    Assert.assertTrue(fileContent.contains("\"shownButtonsCount\":1"));
    Assert.assertTrue(fileContent.contains("\"buildWithSuggestionsCount\":2"));
    Assert.assertTrue(fileContent.contains("\"wrongInvestigationsCount\":4"));
    Assert.assertTrue(fileContent.contains("\"assignedInvestigationsCount\":3"));
    Assert.assertTrue(fileContent.contains("\"processedBuildsCount\":3"));
    Assert.assertTrue(fileContent.contains("\"changesInBuildsCount\":10"));
  }

  @Test
  public void testWriteNotUpdatedStatistics() throws IOException {
    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Files.createDirectory(assignerDataDir);
    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);
    Files.write(myStatisticsPath, readGold("StatisticsDaoTest_TestWriteNotUpdatedStatisticsInitial_Gold.txt").getBytes());
    Statistics statistics = myStatisticsDao.read();

    Files.write(myStatisticsPath, readGold("StatisticsDaoTest_TestWriteNotUpdatedStatistics_Gold.txt").getBytes());
    myStatisticsDao.write(statistics);

    String fileContent = new String(Files.readAllBytes(myStatisticsPath));
    Assert.assertEquals(fileContent, readGold("StatisticsDaoTest_TestWriteNotUpdatedStatistics_Gold.txt").trim());
  }

  @Test
  public void testWriteNotUpdatedStatisticsForSecondTime() throws IOException {
    Assert.assertEquals(myStatisticsDao.read(), new Statistics());
    Statistics statistics = new Statistics();

    statistics.increase(StatisticsValuesEnum.shownButtonsCount, 1);
    statistics.increase(StatisticsValuesEnum.clickedButtonsCount, 2);
    statistics.increase(StatisticsValuesEnum.assignedInvestigationsCount, 3);
    statistics.increase(StatisticsValuesEnum.wrongInvestigationsCount, 4);
    statistics.increase(StatisticsValuesEnum.buildWithSuggestionsCount, 2);
    statistics.increase(StatisticsValuesEnum.savedSuggestionsCount, 3);
    statistics.increase(StatisticsValuesEnum.processedBuildsCount, 3);
    statistics.increase(StatisticsValuesEnum.changesInBuildsCount, 10);
    myStatisticsDao.write(statistics);

    Path assignerDataDir = myPluginsDataDir.resolve(Constants.PLUGIN_DATA_DIR);
    Path myStatisticsPath = assignerDataDir.resolve(Constants.STATISTICS_FILE_NAME);

    Files.write(myStatisticsPath, "UPDATED".getBytes());
    myStatisticsDao.write(statistics);

    String fileContent = new String(Files.readAllBytes(myStatisticsPath));
    Assert.assertEquals(fileContent, "UPDATED");
  }
}