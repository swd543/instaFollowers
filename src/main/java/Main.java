import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    static class BaseEntity{
        @Override
        public String toString() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (JsonProcessingException e) { e.printStackTrace(); }
            return null;
        }
    }

    private enum Action {
        ADDED_FOLLOWERS,
        UNFOLLOWERS
    }

    private static class FollowersHistory extends BaseEntity {
        @JsonProperty("deltas")
        private Map<Date, Tuple<Action, Collection<Person>>> deltas;
        @JsonProperty("initialFollowers")
        private Tuple<Date, Collection<Person>> initialFollowers;

        Collection<Person> compile(){
            var toReturn = new ArrayList<>(initialFollowers.value);
            deltas.entrySet()
                    .parallelStream()
                    .forEach(d->{
                        if(d.getValue().key==Action.ADDED_FOLLOWERS) toReturn.addAll(d.getValue().value);
                        if(d.getValue().key==Action.UNFOLLOWERS) toReturn.removeAll(d.getValue().value);
                    });
            return toReturn;
        }

        void computeDeltas(Collection<Person> newFollowers){
            var oldFollowers = compile();
            var addList=newFollowers.parallelStream().filter(nf->!oldFollowers.contains(nf)).collect(Collectors.toUnmodifiableList());  // new followers
            var unfollowers=oldFollowers.parallelStream().filter(of->!newFollowers.contains(of)).collect(Collectors.toUnmodifiableList());  // unfollowers
            if(!addList.isEmpty()) {
                System.err.println(Action.ADDED_FOLLOWERS+" "+addList);
                deltas.put(new Date(),new Tuple<>(Action.ADDED_FOLLOWERS,addList));
            }
            if(!unfollowers.isEmpty()){
                System.err.println(Action.UNFOLLOWERS+" "+unfollowers);
                deltas.put(new Date(),new Tuple<>(Action.UNFOLLOWERS,unfollowers));
            }
            System.err.println("Deltas computed! "+deltas);
        }

        public String stringify() {
            return "FollowersHistory{" +
                    "initialFollowers=" + initialFollowers.key +"-"+ initialFollowers.value+
                    ", deltas=" + deltas +
                    '}';
        }
    }

    private static class QP extends BaseEntity{
        @JsonProperty("id")
        private Long id=1816987582L;
        @JsonProperty("include_reel")
        private Boolean includeReel=true;
        @JsonProperty("fetch_mutual")
        private Boolean fetchMutual=true;
        @JsonProperty("first")
        private Integer first=200;
        @JsonProperty("after")
        private String after;
    }

    private static class Person extends BaseEntity{
        @JsonProperty("id")
        private Long id=1816987582L;
        @JsonProperty("username")
        private String username;
        @JsonProperty("full_name")
        private String fullName;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return Objects.equals(id, person.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class Page extends BaseEntity{
        @JsonProperty("has_next_page")
        private Boolean hasNextPage;
        @JsonProperty("end_cursor")
        private String endCursor;
    }

    @JsonFormat(shape= JsonFormat.Shape.OBJECT)
    private static class Tuple<K,V> extends BaseEntity implements Map.Entry<K, V> {
        private K key;
        private V value;

        public Tuple() { }

        public Tuple(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public Tuple<K, V> setKey(K key) {
            this.key = key;
            return this;
        }

        @Override
        public K getKey() { return key; }

        @Override
        public V getValue() { return value; }

        @Override
        public V setValue(V v) {
            this.value=v;
            return this.value;
        }
    }

    private static final String DOC_TITLE="Instagram";

    public static void main(String[] args) throws IOException, URISyntaxException {
        try(var c=new DefaultHttpClient()){
            var followers=getAllFollowers(c);
            System.err.println("Current followers : "+followers.size());

            var previousFollowersOptional=Dumper.get(DOC_TITLE,c);
            previousFollowersOptional.ifPresentOrElse(f->{
                try {
                    var objectMapper=new ObjectMapper();
                    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
                    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                    var pf=objectMapper.readValue(f.body, FollowersHistory.class);
                    var pfc=pf.compile();
                    System.err.println("Previous followers : "+pfc.size());
                    pf.computeDeltas(followers);
                    Dumper.addUpdate(DOC_TITLE,pf.toString(),c);
                } catch (IllegalArgumentException | IOException e) {
                    e.printStackTrace();
                }
            },()->{
                try {
                    var toDump=new FollowersHistory();
                    toDump.initialFollowers= new Tuple<>(new Date(), followers);
                    toDump.deltas=Map.of();
                    Dumper.addUpdate(DOC_TITLE,toDump.toString(),c);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static Collection<Person> getAllFollowers(HttpClient c) throws URISyntaxException, IOException {
        Page page=null;
        Collection<Person> followers=new ArrayList<>();
        do{
            var query=new QP();
            if(null!=page) query.after=page.endCursor;
            var g=new URIBuilder("https://www.instagram.com/graphql/query/")
                    .addParameter("query_hash","c76146de99bb02f6415203be841dd25a")
                    .addParameter("variables",query.toString());
            var get=new HttpGet(g.build());
            get.addHeader("cookie","mid=Xa5w9wALAAFPOGYDf5e2Iklf0MtQ; fbsr_124024574287414=xqw_273E7RfSLjlpPbhFnRnFHgg8w7aPpT5A2WzUUj0.eyJ1c2VyX2lkIjoiMTAwMDAxMTE4ODM2ODI1IiwiY29kZSI6IkFRQndqbS1xemJTT1dydnZkbC1CSGpzNmtqaEJQQXlEOXgxczh0d2pKSUliZnlUSkY2Y2dNaXN5WFhESE5yeVk1ZTJYSllOU1p5ZDFER3dxUHhUelJlOU1iaDJGTkdwQ1BrSC1MZGRSdktuT3VfdVltQkVTdnE3UEllbW4wWWhpczhid0ZvTGwyaE9MUmgxY3RDLU1xRXc3WC13N2RpeVh2SVc2Qk9oZGlVMWt2NUI4ZHhFM05KWWJSNERXVUFLZXZ4bDJXUnc0VmZoSV9Ob3dWeDFXVDJkQ0VIekJFNzVsVEUyc0NNNDhWR00yTy1pTmF2dWNlV3ViZnpndU15STNKZEtvbHI2RUpVSDlTY1dscGR6ekF0RXpnUDBfTy0zcXVPaVVrc3k4eVlYUVh1Qmw2ZldvdGRBcWt4X2lyX1FPbzlERDhqdFRqc1RBS1BfYy1KaEd4NER2OUMxSkQ1UWRVYThZSVBza1VpSzVfdyIsIm9hdXRoX3Rva2VuIjoiRUFBQnd6TGl4bmpZQkFKdnVTTXhFRkZoSGd0em1WWkFUZFpDYjljaE1UVnQxUDRyOTRyUTBoSzJVT1hxanBBYVhHaTZzc2UyUmxhZ1FENWJmRzNyR0xYRkJBNFZWaHp6SkV0U2laQjlqblFGWHV5UGNNVmRpNU4wczREUlJFaXgzTzFSWkNubHFMTHZXbFpCNm5oN1U0dVY3RDYzdTR5WVFpMElPbG16b0Z0NUk1aGV6WkM5WkFRcyIsImFsZ29yaXRobSI6IkhNQUMtU0hBMjU2IiwiaXNzdWVkX2F0IjoxNTcxNzEzMjcxfQ; csrftoken=wA8jfZXE6CSydwb1qzY2G2eWszbGpznf; ds_user_id=1816987582; sessionid=1816987582%3Ap4A4g7o5NV1cdk%3A19; ig_did=2DD2C772-C481-4A6A-AC35-CCFC956885C5; shbid=627; shbts=1582539735.7249663; rur=FTW; ig_cb=1; urlgen=\"{\\\"2001:1c05:2405:e100:4180:cc03:a340:e58a\\\": 33915\\054 \\\"2001:1c05:2405:e100:cc47:aa0d:7443:cc2b\\\": 33915\\054 \\\"213.127.123.167\\\": 6830\\054 \\\"2001:1c05:2405:e100:c78:6e95:675d:e000\\\": 33915}:1j6jJ7:yN35JAU9N5EAVG6GafpfC7KFFMg\"");
            var r=c.execute(get);
            System.err.println(r);
            var s= EntityUtils.toString(r.getEntity());
            System.err.println(s);
            var x=getPagewisePeople(s);
            page=x.key;
            followers.addAll(x.value);
            if(!x.key.hasNextPage) break;
        } while (true);
        return followers;
    }

    private static Tuple<Page, Collection<Person>> getPagewisePeople(String jsonString) throws JsonProcessingException {
        var toReturn=new Tuple<Page, Collection<Person>>();
        var objectMapper=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var node=objectMapper.readValue(jsonString, JsonNode.class);
        node=navigate(node, new String[]{"data","user","edge_followed_by"});
        var pageNode=navigate(node,new String[]{"page_info"});
        toReturn.key= objectMapper.readValue(pageNode.toString(), Page.class);
        var people=new ArrayList<Person>();
        var peopleNode=navigate(node,new String[]{"edges"});
        peopleNode.elements().forEachRemaining(pn->{
            var personNode=navigate(pn, new String[]{"node"});
            try {
                var person=objectMapper.readValue(personNode.toString(),Person.class);
                people.add(person);
            } catch (JsonProcessingException e) { e.printStackTrace(); }
        });
        toReturn.setValue(people);
        return toReturn;
    }

    static JsonNode navigate(JsonNode n, final String[] hierarchy){
        for(var i:hierarchy){ if(n.has(i)){ n=n.get(i); } }
        return n;
    }
}
