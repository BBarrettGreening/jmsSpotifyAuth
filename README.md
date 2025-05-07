# Spotify Playlist Authentication

This Spring Boot application authenticates with Spotify using the Jurassic Music Society website as a callback endpoint. Once authenticated, it can be used to manage Spotify playlists.

## Prerequisites

- Java 23 or higher
- Spotify Developer Account
- Registered Spotify Application with callback URL set to: `https://jurassicmusicsociety.com/api/callback`

## Environment Setup

1. Clone this repository
2. Create a `.env` file in the root directory of the project based on the `.env.example` template
3. Fill in your Spotify Developer credentials in the `.env` file:

```
SPOTIFY_CLIENT_ID=your_spotify_client_id_here
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
```

## Setting Environment Variables

### Windows

Using Command Prompt:
```
set SPOTIFY_CLIENT_ID=your_spotify_client_id_here
set SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
```

Using PowerShell:
```
$env:SPOTIFY_CLIENT_ID="your_spotify_client_id_here"
$env:SPOTIFY_CLIENT_SECRET="your_spotify_client_secret_here"
```

### macOS/Linux

```
export SPOTIFY_CLIENT_ID=your_spotify_client_id_here
export SPOTIFY_CLIENT_SECRET=your_spotify_client_secret_here
```

## Running the Application

1. Set the environment variables as described above
2. Start the application:

```
./gradlew bootRun
```

Or build and run the JAR:

```
./gradlew build
java -jar build/libs/spotifyPlaylistAuth-0.0.1-SNAPSHOT.jar
```

3. Access the application at `http://localhost:8080`

## How It Works

1. The application redirects users to the Spotify authorization page
2. After logging in and authorizing the application, Spotify redirects to the Jurassic Music Society callback endpoint
3. This application retrieves the token information from the JMS callback endpoint
4. The tokens are used to fetch and display the user's Spotify profile information

## Spotify Developer Setup

1. Create a Spotify Developer account at [developer.spotify.com](https://developer.spotify.com/)
2. Create a new application in the Spotify Developer Dashboard
3. Add `https://jurassicmusicsociety.com/api/callback` to the Redirect URIs in your Spotify application settings
4. Copy the Client ID and Client Secret to your environment variables

## Security Notes

- Never commit your Spotify Client ID and Secret to version control
- The application currently displays access tokens on the success page - in a production environment, these should be securely stored
- For improved security in a production environment, implement token refresh handling and secure cookie storage
