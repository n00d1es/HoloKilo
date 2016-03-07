using UnityEngine;
using System.Collections;

public class Rotate : MonoBehaviour {

	// Use this for initialization
	void Start () {
	
	}
	
	// Update is called once per frame
	void Update () {
        transform.Rotate(Time.deltaTime * 30f, Time.deltaTime * 30f, Time.deltaTime * 30f);
	}
}
