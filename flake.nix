{
  description = "RowMania: Clojure-Webanwendung mit Datahike auf SQLite";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      clj-nix,
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
            installCli = lib.mkEnableOption "das Kommando rowmania-ctl, das rowmania-cli als Dienstnutzer auf der Dienst-Datenbank ausführt";
          };

          config = lib.mkIf cfg.enable {
            environment.systemPackages = lib.optional cfg.installCli (
              pkgs.writeShellScriptBin "rowmania-ctl" ''
                exec systemd-run --pty --wait --collect --quiet \
                  -p User=rowmania -p DynamicUser=yes -p StateDirectory=rowmania \
                  -E ROWMANIA_DB_PATH=/var/lib/rowmania/rowmania.sqlite \
                  ${self.packages.${pkgs.system}.cli}/bin/rowmania-cli "$@"
              ''
            );

            systemd.services.rowmania = {
              description = "RowMania";
              wantedBy = [ "multi-user.target" ];
              after = [ "network-online.target" ];
              wants = [ "network-online.target" ];
              environment = {
                ROWMANIA_HOST = cfg.host;
                ROWMANIA_PORT = toString cfg.port;
                ROWMANIA_DB_PATH = "/var/lib/rowmania/rowmania.sqlite";
              };
              serviceConfig = {
                ExecStart = "${self.packages.${pkgs.system}.default}/bin/start-server";
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
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ clj-nix.overlays.default ];
        };
        jdk = pkgs.jdk25;
        clojure = pkgs.clojure.override { jdk = jdk; };
        tools = [
          clojure
          jdk
          pkgs.git
        ];

        # Uberjars mit Abhängigkeiten aus deps-lock.json
        # (aktualisieren: nix run github:jlesquembre/clj-nix#deps-lock)
        mkRowmaniaJar =
          { name, main-ns }:
          pkgs.mkCljBin {
            inherit jdk name main-ns;
            jdkRunner = jdk;
            projectSrc = ./.;
            version = "0.1.0";
            # Die mitgelieferte libsqlite3 lädt unter NixOS nicht; nixpkgs' sqlite kommt über LD_LIBRARY_PATH der Wrapper
            java-opts = [
              "--enable-native-access=ALL-UNNAMED"
              "-Dsqlite4clj.native-lib=system"
            ];
          };
        rowmaniaJar = mkRowmaniaJar {
          name = "rowmania/rowmania";
          main-ns = "rowmania.core";
        };
        rowmaniaCliJar = mkRowmaniaJar {
          name = "rowmania/rowmania-cli";
          main-ns = "rowmania.cli";
        };

        startServer = pkgs.writeShellApplication {
          name = "start-server";
          runtimeInputs = [ rowmaniaJar ];
          text = ''
            # coffi lädt libsqlite3 zur Laufzeit
            export LD_LIBRARY_PATH="${pkgs.sqlite.out}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            if [ -f "$PWD/.env" ]; then
              set -a
              # shellcheck disable=SC1091
              . "$PWD/.env"
              set +a
            fi
            exec rowmania
          '';
        };

        cli = pkgs.writeShellApplication {
          name = "rowmania-cli";
          runtimeInputs = [ rowmaniaCliJar ];
          text = ''
            # coffi lädt libsqlite3 zur Laufzeit
            export LD_LIBRARY_PATH="${pkgs.sqlite.out}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            exec rowmania-cli "$@"
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
