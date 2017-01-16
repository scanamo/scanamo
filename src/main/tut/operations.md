---
layout: docs
title: Operations 
position: 1
---

### Operations

Scanamo supports all the DynamoDB operations that interact with individual items in DynamoDB tables:

 * [Put](put-get.html) for adding a new item, or replacing an existing one
 * [Get](put-get.html) for retrieving an item by a fully specified key
 * Delete for removing an item
 * [Update](updating.html) for updating some portion of the fields of an item, whilst leaving the rest 
 as is
 * [Scan](scanning.html) for retrieving all elements of a table
 * [Query](querying.html) for retrieving all elements with a given hash-key and a range key that matches
 some criteria