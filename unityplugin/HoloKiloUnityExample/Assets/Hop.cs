using UnityEngine;
using System.Collections;

public class Hop : MonoBehaviour
{
    public float hopHeight = 5f;
    public float hopSpeed = 10f;
    private float startHeight;
    private Vector3 position = new Vector3();
    // Use this for initialization
    void Start () {
        startHeight = transform.localPosition.y;
        position = new Vector3(transform.localPosition.x, 0, transform.localPosition.z);

    }

    float time = 0f;
	// Update is called once per frame
	void Update () {
        time += Time.deltaTime;
        float hop = Mathf.Sin(time * hopSpeed) * hopHeight;
        if (hop > 0f)
        {
            position.y = startHeight + hop;
            transform.localPosition = position;
        }
	}
}
