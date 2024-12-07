import types

from mcp.server import Server

class McpServer:

    def __init__(self, name: str):
        self.server = Server(name)


    def register_handlers(self):
        app = self.server
