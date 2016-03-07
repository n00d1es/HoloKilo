package net.kajos.holokilo.Processors;

import java.util.*;

/**
 * Created by Kajos on 7-1-2016.
 */
public class BlobBundle {
    public List<Blob> list = new LinkedList<Blob>();
    public ArrayList<BlobRelation> sorted = new ArrayList<BlobRelation>();
    public HashMap<Blob, Blob> neighbors = new HashMap<Blob, Blob>();

    static class BlobRelation implements Comparable<BlobRelation>{
        public Blob a, b;
        public double distance;

        public BlobRelation(Blob a, Blob b) {
            this.a = a;
            this.b = b;
            distance = a.distance(b);
        }

        @Override
        public int compareTo(BlobRelation another) {
            if (distance < another.distance) {
                return 1;
            } else if (distance == another.distance) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    public BlobBundle() {

    }

    public void findNeighborsTo(BlobBundle compareBlobs) {
        neighbors.clear();
        sorted.clear();

        for (int i = 0; i < list.size(); i++) {
            Blob a = list.get(i);
            for (int k = 0; k < compareBlobs.size(); k++) {
                Blob b = compareBlobs.get(k);
                sorted.add(new BlobRelation(a,b));
            }
        }

        Collections.sort(sorted);

        for (int i = 0; i < sorted.size(); i++) {
            Blob a = sorted.get(i).a;
            if (!neighbors.containsKey(a)) {
                Blob b = sorted.get(i).b;
                neighbors.put(a, b);
            }
        }
    }

    public Blob getNeighbor(Blob blob) {
        return neighbors.get(blob);
    }

    public void add(Blob blob) {
        list.add(blob);
    }

    public void addAll(BlobBundle blobs) {
        list.addAll(blobs.list);
    }

    public int size() {
        return list.size();
    }

    public void clear() {
        list.clear();
    }

    public Blob get(int i) {
        return list.get(i);
    }
}
