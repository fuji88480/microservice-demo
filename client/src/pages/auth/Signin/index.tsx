import { AuthContext } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useSignin } from '@/hooks/useSignin';
import { Label } from '@radix-ui/react-label';
import { useContext, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';

export default function Signin() {
  const { mutate } = useSignin();
  const navigate = useNavigate();
  const { refresh } = useContext(AuthContext);

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);

    mutate(
      {
        email: fd.get('email') as string,
        password: fd.get('password') as string,
      },
      {
        onSuccess: () => {
          refresh?.();
          setTimeout(() => {
            navigate('/');
          }, 500);
        },
      },
    );
  };
  return (
    <div className="flex justify-center font-mono">
      <div className="space-y-6 p-10 mt-50 rounded-2xl border">
        <div className="space-y-2">
          <h1 className="text-2xl">ログイン</h1>
          <p className="text-sm text-muted-foreground">
            GitTixにログイン
          </p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <Label htmlFor="email">E-Mail</Label>
            <Input
              id="email"
              type="email"
              name="email"
              placeholder="you@example.com"
            />
          </div>

          <div className="mb-10">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              name="password"
              placeholder="********"
            />
          </div>

          <Button variant="outline" className="w-full bg-gray-100">
            ログイン
          </Button>

          <Link to="/auth/signup">
            <Button
              variant="destructive"
              className="w-full bg-pink-700"
            >
              新規登録
            </Button>
          </Link>
        </form>
      </div>
    </div>
  );
}
