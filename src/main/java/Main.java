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
import org.apache.http.impl.client.HttpClientBuilder;
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
                    .stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEachOrdered(d->{
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
        try(var c=HttpClientBuilder.create().build()){
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
                    .addParameter("query_hash",System.getenv("INSTA_QUERY_HASH"))
                    .addParameter("variables",query.toString());
            var get=new HttpGet(g.build());
            get.addHeader("cookie",System.getenv("INSTA_COOKIE"));
            var r=c.execute(get);
            System.err.println(r.getStatusLine());
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
