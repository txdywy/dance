package com.bumptech.glide.samples.flickr.api;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.json.JSONException;

import java.util.Collection;
import java.util.List;

/**
 * Handles photo list responses and errors from Flickr API calls.
 */
final class MeiQueryResponseListener implements Response.Listener<String>,
    Response.ErrorListener {
  private final PhotoJsonStringParser parser;
  private final Query query;
  private final Collection<MeiApi.QueryListener> listeners;

  MeiQueryResponseListener(PhotoJsonStringParser parser, Query query,
                           Collection<MeiApi.QueryListener> listeners) {
    this.parser = parser;
    this.query = query;
    this.listeners = listeners;
  }

  @Override
  public void onResponse(String response) {
    try {
      notifySuccess(parser.mei_parse(response));
    } catch (JSONException e) {
      notifyFailed(e);
    }
  }

  @Override
  public void onErrorResponse(VolleyError error) {
    notifyFailed(error);
  }

  private void notifySuccess(List<Photo> results) {
    for (MeiApi.QueryListener listener : listeners) {
      listener.onSearchCompleted(query, results);
    }
  }

  private void notifyFailed(Exception e) {
    for (MeiApi.QueryListener listener : listeners) {
      listener.onSearchFailed(query, e);
    }
  }
}
