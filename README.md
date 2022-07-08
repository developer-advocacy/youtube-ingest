# Youtube Ingest

This module ingests data about videos on YouTube.

## You'll Need an API Key

Follow the steps below to create an API Key.

* Go to the [Google Developer Console](https://console.cloud.google.com/).
* Create a new project. You can also select the existing project.
* Type the name of your project. Google Console will create the unique project ID.
* After creating a project, it will appear on top of the left sidebar.
* Click on Library. You will see a list of Google APIs.
* Search for YouTube Data API and enable it.
* Click on the Credentials. Select the API key under Create credentials.
* Copy the API key. We will require it in the next step.


## To Do
* make sure that we note the channel ID when recording a video. Right now there's no 
* make sure we handle video tags! right now they're just being ignored. 
* create some sort of reporting (SQL views, anyone?) for the most liked videos so we can then use that figure out what to tweet, whats popular, what to invest more in, etc. 
