/**
 * This file is part of General Entity Annotator Benchmark.
 *
 * General Entity Annotator Benchmark is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * General Entity Annotator Benchmark is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with General Entity Annotator Benchmark.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.aksw.gerbil.dataset.impl.micro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aksw.gerbil.transfer.nif.Marking;
import org.aksw.gerbil.transfer.nif.data.NamedEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Microposts2014DatasetMentionSearchTest {

    @Parameters
    public static Collection<Object[]> data() {
        List<Object[]> testConfigs = new ArrayList<Object[]>();
        testConfigs.add(new Object[] { "NOTW phone hacking",
                "Rupert #Murdoch, asked who was responsible for #NOTW phone #hacking? 'The people I trusted & maybe the people they trusted'",
                "#NOTW phone #hacking" });
        testConfigs.add(new Object[] { "Amy Winehouse",
                "#Amy #Winehouse Is #Dead After a Suspected Drug Overdose  http://t.co/9KBWCeN via @YahooNews",
                "#Amy #Winehouse" });
        testConfigs.add(new Object[] { "White Sox",
                "#MLB Live Score Update #White #Sox (4) - #Indians (2) Final Play By Play Click link: http://rotoinfo.com/gameview?310724105",
                "#White #Sox" });
        return testConfigs;
    }

    private String mention;
    private String tweet;
    private String expectedMention;

    public Microposts2014DatasetMentionSearchTest(String mention, String tweet, String expectedMention) {
        this.mention = mention;
        this.tweet = tweet;
        this.expectedMention = expectedMention;
    }

    @Test
    public void test() {
        String line[] = new String[] { "tweet-ID", "orig tweet text", mention, "mention-URI" };
        List<Marking> markings = Microposts2014Dataset.findMarkings(line, tweet);
        Assert.assertNotNull(markings);
        Assert.assertTrue(markings.size() > 0);
        Assert.assertTrue(markings.get(0) instanceof NamedEntity);
        NamedEntity ne = (NamedEntity) markings.get(0);
        String mention = tweet.substring(ne.getStartPosition(), ne.getStartPosition() + ne.getLength());
        Assert.assertEquals(expectedMention, mention);
    }
}
