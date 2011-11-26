/*
 * Eyal Zohar, The Open University of Israel, 2008
 */
package il.ac.openu.eyalzo.sim3;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Counter of items per key. Use it to count instances in list or map.
 * 
 * @author Eyal Zohar
 */
public class MapCounter<K>
{
    protected Map<K, Long> mapCounter = Collections.synchronizedMap(new HashMap<K, Long>());

    /**
     * Add to counter of this key.
     * 
     * @param key
     *                Key.
     * @param toAdd
     *                How much to add to this key's counter.
     * @return Updated count, after add.
     */
    public synchronized long add(K key, long toAdd)
    {
	Long prevValue = mapCounter.get(key);

	//
	// If key is new put for the first time
	//
	if (prevValue == null)
	{
	    mapCounter.put(key, toAdd);
	    return toAdd;
	}

	//
	// Key already exists, to add to current
	//
	long newValue = prevValue + toAdd;
	mapCounter.put(key, newValue);
	return newValue;
    }

    /**
     * Add counters from another map-counter.
     * 
     * @param other
     *                Another map-counter. map-counter, so a local copy better
     *                be given and not a live object.
     */
    public synchronized void addAll(MapCounter<K> other)
    {
	Iterator<Entry<K, Long>> it = other.entrySet().iterator();
	while (it.hasNext())
	{
	    Entry<K, Long> entry = it.next();
	    this.add(entry.getKey(), entry.getValue());
	}
    }

    /**
     * Add 1 to counter of this key.
     * 
     * @param key
     *                Key.
     * @return Updated count, after add.
     */
    public synchronized long inc(K key)
    {
	return this.add(key, 1L);
    }

    /**
     * @return Entry set, for iterator over the original internal map.
     */
    public synchronized Set<Entry<K, Long>> entrySet()
    {
	return mapCounter.entrySet();
    }

    /**
     * @return Key set, for iterator over the original internal map.
     */
    public synchronized Set<K> keySet()
    {
	return mapCounter.keySet();
    }

    /**
     * Gets the numeric value stored for this key, or zero if not found.
     * 
     * @param key
     *                The given key.
     * @return The numeric value stored for this key, or zero if not found.
     */
    public synchronized long get(K key)
    {
	Long value = mapCounter.get(key);
	if (value == null)
	    return 0;
	return value.longValue();
    }

    /**
     * @return Sum of al counts.
     */
    public synchronized long getSum()
    {
	long sum = 0;

	for (long val : mapCounter.values())
	{
	    sum += val;
	}

	return sum;
    }

    /**
     * @return Average count per key, by sum divided by number of keys.
     */
    public synchronized float getAverage()
    {
	if (mapCounter.isEmpty())
	    return 0;

	return (float) (((double) (this.getSum())) / mapCounter.size());
    }

    /**
     * @return Number of keys in this map.
     */
    public int size()
    {
	return mapCounter.size();
    }

    @Override
    public synchronized String toString()
    {
	return toString("\n", "\t", null);
    }

    public synchronized String toString(MapCounter<K> mapOccurrences)
    {
	return toString("\n", "\t", mapOccurrences);
    }

    /**
     * @param keySeparator
     *                If not null, keys will be returned, and each line (except
     *                for the last) will end with this string.
     * @param valueSeparator
     *                If not null, values will be returned, and seperated from
     *                keys (or other values, if keys are not returned) with this
     *                string.
     * @param mapOccurrences
     *                Optional, for count and average per key.
     */
    public synchronized String toString(String keySeparator, String valueSeparator, MapCounter<K> mapOccurrences)
    {
	StringBuffer buffer = new StringBuffer(1000);
	boolean first = true;

	Iterator<Entry<K, Long>> it = getSortedByKeyDup().entrySet().iterator();
	while (it.hasNext())
	{
	    Entry<K, Long> entry = it.next();

	    if (keySeparator != null)
	    {
		if (first)
		{
		    first = false;
		} else
		{
		    buffer.append(keySeparator);
		}
		buffer.append(entry.getKey().toString());
	    }

	    if (valueSeparator != null)
	    {
		buffer.append(valueSeparator);

		buffer.append(entry.getValue().toString());

		//
		// Optional occurrences counter for average per key
		//
		if (mapOccurrences != null)
		{
		    Long curCount = mapOccurrences.get(entry.getKey());
		    if (curCount > 0)
		    {
			buffer.append(valueSeparator);

			buffer.append(curCount.toString());

			buffer.append(valueSeparator);

			buffer.append(String.format("%.2f", ((float) entry.getValue()) / curCount));
		    }
		}
	    }
	}

	return buffer.toString();
    }

    /**
     * Clear all items and their counters.
     */
    public synchronized void clear()
    {
	mapCounter.clear();
    }

    public synchronized boolean isEmpty()
    {
	return mapCounter.isEmpty();
    }

    /**
     * @return New map, sorted by the keys, in ascending order.
     */
    public synchronized SortedMap<K, Long> getSortedByKeyDup()
    {
	return new TreeMap<K, Long>(mapCounter);
    }
}
