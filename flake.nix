{
  description = "RowMania: Clojure-Webanwendung mit Datahike auf SQLite";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    {
      nixosModules.default =
        {
          config,
          lib,
          pkgs,
          ...
        }:
        let
          cfg = config.services.rowmania;
        in
        {
          options.services.rowmania = {
            enable = lib.mkEnableOption "RowMania";
            host = lib.mkOption {
              type = lib.types.str;
              default = "127.0.0.1";
            };
            port = lib.mkOption {
              type = lib.types.port;
              default = 8080;
            };
          };

          config = lib.mkIf cfg.enable {
            systemd.services.rowmania = {
              description = "RowMania";
              wantedBy = [ "multi-user.target" ];
              after = [ "network-online.target" ];
              wants = [ "network-online.target" ];
              environment = {
                ROWMANIA_HOST = cfg.host;
                ROWMANIA_PORT = toString cfg.port;
                ROWMANIA_DB_PATH = "/var/lib/rowmania/rowmania.sqlite";
                # clj legt Abhängigkeits-Caches unter HOME/XDG ab
                HOME = "/var/lib/rowmania";
              };
              serviceConfig = {
                ExecStart = "${self.packages.${pkgs.system}.default}/bin/start-server";
                WorkingDirectory = "${self}";
                DynamicUser = true;
                StateDirectory = "rowmania";
                Restart = "on-failure";
              };
            };
          };
        };
    }
    // flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk25;
        clojure = pkgs.clojure.override { jdk = jdk; };
        tools = [
          clojure
          jdk
          pkgs.git
        ];

        startServer = pkgs.writeShellApplication {
          name = "start-server";
          runtimeInputs = tools;
          text = ''
            if [ -f "$PWD/.env" ]; then
              set -a
              # shellcheck disable=SC1091
              . "$PWD/.env"
              set +a
            fi
            exec clj -X:start-server
          '';
        };

        cli = pkgs.writeShellApplication {
          name = "rowmania-cli";
          runtimeInputs = tools;
          text = ''
            exec clj -M:cli "$@"
          '';
        };

        devServer = pkgs.writeShellApplication {
          name = "dev-server";
          runtimeInputs = tools;
          text = ''
            exec clj -X:watch
          '';
        };
      in
      {
        packages.default = startServer;
        packages.dev-server = devServer;
        packages.cli = cli;

        apps.dev = {
          type = "app";
          program = "${devServer}/bin/dev-server";
        };

        apps.default = {
          type = "app";
          program = "${startServer}/bin/start-server";
        };

        devShells.default = pkgs.mkShell {
          packages = tools ++ [ pkgs.babashka pkgs.html-tidy ];
          JAVA_HOME = jdk.home;
        };
      }
    );
}
