import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class Dumper {
    private static class Note extends Main.BaseEntity{
        @JsonProperty("n")
        private NotePost post;
        @JsonProperty("modBinary")
        private Long modBinary=null;
        @JsonProperty("force")
        private Boolean force=true;
        public Note(){ }
        public Note(NotePost post) { this.post = post; }
    }

    static class NotePost extends Main.BaseEntity{
        @JsonProperty("ID")
        private Long id;
        @JsonProperty("Title")
        @JsonAlias("T")
        private String title;
        @JsonProperty("Body")
        String body;
        @JsonProperty("Selection")
        private Integer selection;
        @JsonProperty("Cr") @JsonFormat(pattern = "yyyy-MMM-dd HH:mm:ss")
        private Date created;
        @JsonProperty("Mod") @JsonFormat(pattern = "yyyy-MMM-dd HH:mm:ss")
        private Date modified;
    }

    static class GetQuery extends Main.BaseEntity {
        @JsonProperty("id")
        private Long id;
    }

    static HttpResponse addUpdate(String title, String content, HttpClient client) throws IOException {
        var notes=getAll(client);
        var id=notes.parallelStream()
                .filter(Objects::nonNull)
                .filter(n->n.title.equals(title))
                .mapToLong(n->n.id)
                .findFirst();
        return add(title, content, id.orElse(-1), client);
    }

    private static HttpResponse add(String title, String content, HttpClient client) throws IOException { return add(title, content,-1L, client); }

    private static HttpResponse add(String title, String content, Long id, HttpClient client) throws IOException {
        var post=new HttpPost("https://scribz.net/Note/StoreNote");
        post.addHeader("Content-Type","application/json");
        post.addHeader("Cookie",System.getenv("SCRIBD_COOKIE"));
        var message=new NotePost();
        message.id=id;
        message.body=content;
        message.title=title;
        message.selection=content.length();
        var note=new Note(message);
        post.setEntity(new StringEntity(note.toString()));
        return client.execute(post);
    }

    private static List<NotePost> getAll(HttpClient client) throws IOException {
        var post=new HttpPost("https://scribz.net/Note/LoadStubs");
        post.addHeader("Content-Type","application/json");
        post.addHeader("Cookie",System.getenv("SCRIBD_COOKIE"));
        var objectMapper=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var r=client.execute(post);
        var jsonString=EntityUtils.toString(r.getEntity());
        var node=objectMapper.readValue(jsonString, JsonNode.class);
        node=navigate(node,new String[]{"Stubs"});
        List<NotePost> notes=new ArrayList<>();
        node.elements().forEachRemaining(n-> {
            try {
                notes.add(objectMapper.readValue(n.toString(), NotePost.class));
            } catch (JsonProcessingException e) { e.printStackTrace(); }
        });
        return notes;
    }

    static Optional<NotePost> get(String title, HttpClient client) throws IOException {
        var id=getAll(client).parallelStream().filter(n->n.title.equals(title)).mapToLong(n->n.id).findFirst();
        if(id.isPresent()){
            var post=new HttpPost("https://scribz.net/Note/LoadNote");
            post.addHeader("Content-Type","application/json");
            post.addHeader("Cookie",System.getenv("SCRIBD_COOKIE"));
            var query=new GetQuery();
            query.id=id.getAsLong();
            var objectMapper=new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(query)));
            var r=client.execute(post);
            var jsonString=EntityUtils.toString(r.getEntity());
            var node=objectMapper.readValue(jsonString, JsonNode.class);
            node=navigate(node,new String[]{"Note"});
            return Optional.of(objectMapper.readValue(node.toString(), NotePost.class));
        }
        return Optional.empty();
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        try(var c= HttpClientBuilder.create().build()){
            var r=addUpdate("Buga","Boxes", c);
            System.out.println(r);
        }
    }

    private static JsonNode navigate(JsonNode n, final String[] hierarchy){
        for(var i:hierarchy){ if(n.has(i)){ n=n.get(i); } }
        return n;
    }
}
