import java.io.Serializable;

public class Pair<K, V> implements Serializable {
    private K key;
    private V value;

    public Pair(K key, V value){
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public void setKey(K key){ this.key = key; }

    @Override
    public String toString() {
        return "(" + key + ", " + value + ')';
    }
}
