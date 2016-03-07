package net.kajos.holokilo.Processors;

import java.util.*;

/**
 * Created by Kajos on 5-1-2016.
 */
public class Blob {
    public int x, y;
    public int xMin, yMin, xMax, yMax;
    public int refl, rRefl, gRefl, bRefl;
    public int mass, rMass, bMass, gMass;
    public int label;
    public boolean isLight = true;

    public Map<Blob, Double> distances;

    public void addDistances(List<Blob> list) {
        Map<Blob, Double> unsortMap = new HashMap<Blob, Double>();
        for (int i = 0; i < list.size(); i++) {
            Blob blob = list.get(i);
            unsortMap.put(blob, distance(blob));
        }
        distances = new TreeMap<Blob, Double>(unsortMap);
    }

    public void copyTo(Blob dest) {
        dest.label = label;
        dest.x = x;
        dest.y = y;
        dest.xMin = xMin;
        dest.xMax = xMax;
        dest.yMin = yMin;
        dest.yMax = yMax;
        dest.refl = refl;
        dest.rRefl = rRefl;
        dest.gRefl = gRefl;
        dest.bRefl = bRefl;
        dest.mass = mass;
    }

    public double distance(Blob blob) {
        int dx = blob.x - x;
        int dy = blob.y - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Blob closest(List<Blob> list) {
        if (list.size() == 0)
            return null;

        double dist = distance(list.get(0));
        Blob close = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            double nd = distance(list.get(i));
            if (nd < dist) {
                dist = nd;
                close = list.get(i);
            }
        }
        return close;
    }

    public Blob furthest(List<Blob> list) {
        if (list.size() == 0)
            return null;

        double dist = distance(list.get(0));
        Blob far = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            double nd = distance(list.get(i));
            if (nd > dist) {
                dist = nd;
                far = list.get(i);
            }
        }
        return far;
    }
}
