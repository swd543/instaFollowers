import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class TestGeneric {
    @JsonFormat(shape= JsonFormat.Shape.NATURAL)
    private static class Tuple<K,V> implements Map.Entry<K,V>{
        private K t;
        private V v;

        public Tuple() { }

        public Tuple(K t, V v) {
            this.t = t;
            this.v = v;
        }

        @Override
        public String toString() {
            return "Gen{" +
                    "t=" + t +
                    ", v=" + v +
                    '}';
        }

        @Override
        public K getKey() {
            return t;
        }

        @Override
        public V getValue() {
            return v;
        }

        @Override
        public V setValue(V v) {
            this.v=v;
            return v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tuple<?, ?> gen = (Tuple<?, ?>) o;
            return Objects.equals(t, gen.t) &&
                    Objects.equals(v, gen.v);
        }

        @Override
        public int hashCode() { return Objects.hash(t, v); }
    }

    private static class P{
        @JsonProperty("v")
        private Tuple<Integer,String > v;

        @Override
        public String toString() {
            return "P{" +
                    "v=" + v +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            P p = (P) o;
            return Objects.equals(v, p.v);
        }

        @Override
        public int hashCode() { return Objects.hash(v); }
    }


    private static class Complex{
        @JsonProperty("a")
        private Tuple<Integer, Collection<String>> a;
        @JsonProperty("b")
        private Map<Integer, Tuple<Integer, Collection<String>>> b;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Complex complex = (Complex) o;
            return Objects.equals(a, complex.a) &&
                    Objects.equals(b, complex.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }

        @Override
        public String toString() {
            return "Complex{" +
                    "a=" + a +
                    ", b=" + b +
                    '}';
        }
    }

    @Test
    void test() throws JsonProcessingException {
        var o=new Tuple<Integer, String>();
        o.t=10;
        o.v="AS";
        serializeThenDeserialize(o, Tuple.class);
        var o1=new P();
        o1.v=o;
        serializeThenDeserialize(o1, P.class);
    }

    @Test
    void complexTest() throws JsonProcessingException {
        var o=new Complex();
        Collection<String> collection= List.of("Hey", "What's", "Up");
        o.a= new Tuple<>(1,collection);
        Collection<String> c2 = new ArrayList<>(collection);
        c2.add("NIHIH");
        o.b=Map.of(10, new Tuple<>(2, c2));
        serializeThenDeserialize(o,Complex.class);
    }

    <T> T serializeThenDeserialize(T object, Class<T> tClass) throws JsonProcessingException {
        var mapper=new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
        var s=mapper.writeValueAsString(object);
        System.err.println(s);
        var o=mapper.readValue(s, tClass);
        System.err.println(o+" "+object);
        Assertions.assertEquals(o,object);
        return o;
    }
}
